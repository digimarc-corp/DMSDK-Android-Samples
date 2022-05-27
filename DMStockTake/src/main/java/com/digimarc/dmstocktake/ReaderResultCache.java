package com.digimarc.dmstocktake;

import android.graphics.Point;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.digimarc.dms.payload.Payload;
import com.digimarc.dms.readers.DataDictionary;
import com.digimarc.dms.readers.Utility;

import java.util.ArrayList;
import java.util.List;

/**
 * This class maintains a cache of read results over the a period of time. This allows an
 * application to maintain a smooth, consistent view of results even when individual codes
 * may not read on every frame.
 * <p>
 * To use the class create an instance of the object and then perform the following steps for
 * each set of ReaderResult data received:
 * <ul>
 *   <li>Call startFrame()
 *   <li>For each payload in the ReaderResult call addReadRecord()
 *   <li>Call endFrame()
 * </ul>
 * <p>
 * Once a frame is completed (i.e. after endFrame() is called) the data can be retrieved with the
 * methods getCurrentResults(), getNewResults() and getRemovedResults().
 */
public class ReaderResultCache
{
    // Set how long should results stay in the cache (time in milliseconds)
    private static final long Max_Interval = 500;

    private static final float Small_Code_Threshold = 0.2f;
    private static final float Large_Code_Threshold = 0.4f;

    private int mEntryCount = 0;

    private static final float Expansion_Small = 2.5f;
    private static final float Expansion_Medium = .8f;
    private static final float Expansion_Large = .35f;

    /**
     * Class that tracks read data and regions.
     */
    public static class ReadData
    {
        private final Payload mPayload;
        private final Region mRegion;
        private final DataDictionary mMetadata;
        public long mReadTime;
        private Region mExpandedRegion;

        // ID is intended as a way of allowing the application to track an individual code
        // and potentially maintain app level metadata on it across frames.
        public int mReadId;

        ReadData( @NonNull final Payload payload, @NonNull final Region region,
                  @NonNull final DataDictionary metadata, long timestamp )
        {
            mPayload = payload;
            mRegion = region;
            mMetadata = metadata;
            mReadTime = timestamp;
            mReadId = 0;
            mExpandedRegion = null;
        }

        void setId( int id )
        {
            mReadId = id;
        }

        int getId()
        {
            return mReadId;
        }

        Payload getPayload()
        {
            return mPayload;
        }

        @Override
        public boolean equals( Object o ) {
            if ( this == o ) return true;
            if ( o == null || getClass() != o.getClass() ) return false;

            ReadData readData = (ReadData) o;

            if ( mReadId != readData.mReadId ) return false;
            return mPayload.equals( readData.mPayload );
        }

        @Override
        public int hashCode() {
            int result = mPayload.hashCode();
            result = 31 * result + mReadId;
            return result;
        }

        boolean isStale( long frameTime )
        {
            return frameTime - mReadTime > Max_Interval;
        }

        @NonNull Region getRegion()
        {
            return mRegion;
        }

        @NonNull List<Point> getRegionPoints()
        {
            return mRegion.getPoints();
        }

        @NonNull DataDictionary getMetadata()
        {
            return mMetadata;
        }

        @NonNull
        Region getExpandedRegion()
        {
            if ( mExpandedRegion == null )
                mExpandedRegion = new Region( mRegion.getExpandedPoints() );

            return mExpandedRegion;
        }
    }

    private final List<ReadData> mData = new ArrayList<>();
    private final List<ReadData> mWorkingList = new ArrayList<>();
    private final List<ReadData> mRemovedPayloads = new ArrayList<>();
    private final List<ReadData> mNewPayloads = new ArrayList<>();
    private long mFrameTime = 0;

    /**
     * Get the read results from the most recent frame.
     * @return List of read data
     */
    public List<ReadData> getCurrentResults()
    {
        return mData;
    }

    /**
     * Get the new reads from the most recent frame. These are results that were not found in
     * the previous frame and appeared in the most recent frame.
     * @return List of read data
     */
    public List<ReadData> getNewResults()
    {
        return mNewPayloads;
    }
    /**
     * Get the removed read results from the most recent frame. These are results that were in
     * the previous frame but are no longer considered valid.
     * @return List of read data
     */
    public List<ReadData> getRemovedResults()
    {
        return mRemovedPayloads;
    }

    /**
     * This method prepares the cache for processing a new set of reads. This must be called before
     * any calls to addReadRecord().
     */
    public void startFrame()
    {
        mWorkingList.clear();
        mRemovedPayloads.clear();
        mNewPayloads.clear();

        mFrameTime = System.currentTimeMillis();
    }

    /**
     * Adds a read and its data to the cache.
     * @param payload  Payload read
     * @param metadata Metadata the accompanied the payload
     * @param rotation Image rotation required for display. This is generally the camera rotation
     *                 (defined as the rotation required to transform a captured image to the
     *                 device orientation) minus the device's current orientation.
     */
    public void addReadRecord( @NonNull final Payload payload, @NonNull final DataDictionary metadata, int rotation )
    {
        Object value = metadata.getValue(DataDictionary.ReadRegion);

        if (value != null)
        {
            Region region = new Region( Utility.applyRotationToPoints( (List<Point>) value, rotation ) );

            ReadData data = new ReadData( payload, region, metadata, mFrameTime );

            ReadData bestMatch = findDuplicate( data );

            if ( bestMatch != null )
            {
                // Found a match. We'll update our new record with the ID from the matching read
                // and then remove the original from our last results

                data.setId( bestMatch.getId() );

                mData.remove( bestMatch );
            }
            else
            {
                // No match found. Set the ID and then add the read to our New results list

                data.setId( mEntryCount++ );

                mNewPayloads.add( data );
            }

            // Whether this matched or not we add it to our current (ongoing) results list
            mWorkingList.add( data );
        }
    }

    /**
     * This method finalizes the data collected during a frame and prepares it for use.
     */
    public void endFrame()
    {
        // Walk through mData and remove all records older than our max interval
        for ( ReadData next : mData )
        {
            if ( !next.isStale( mFrameTime ) )
                mWorkingList.add( next );
            else
                mRemovedPayloads.add( next );
        }

        mData.clear();

        mData.addAll( mWorkingList );
        mWorkingList.clear();
    }

    public long getFrameTime()
    {
        return mFrameTime;
    }

    /**
     * Clear cache.
     */
    public void clear() {
        mData.clear();
        mWorkingList.clear();
        mRemovedPayloads.clear();
    }

    /**
     * Determines if the given payload is a duplicate result. The criteria for a duplicate are:
     * 1. The payloads match.
     * 2. The payloads have overlapping read regions, where read region A has a center point
     * within read region B or vice versa.
     * @param newRead Read data for a new result
     * @return The matching record if the payloads are the same and the regions overlap.
     * Null otherwise.
     */
    @Nullable
    public ReadData findDuplicate(@NonNull final ReadData newRead) {
        for ( ReadData next : mData )
        {
            if ( newRead.getPayload().equals( next.getPayload() ) &&
                    hasOverlap( newRead.getRegion(), next.getExpandedRegion() ) )
                return next;
        }

        return null;
    }

    private boolean hasOverlap( Region a, Region b) {
        return (a.containsPoint(b.getCenter()) ||
                b.containsPoint(a.getCenter()));

    }


    static class Region {

        @NonNull
        private final List<Point> mCorners;

        @NonNull
        private final Point mCenter;

        Region(@NonNull List<Point> corners) {
            mCorners = corners;
            mCenter = centerPoint(mCorners);
        }

        @NonNull
        Point getCenter() {
            return mCenter;
        }

        @NonNull
        List<Point> getPoints()
        {
            return mCorners;
        }

        /**
         * Determines whether given point is inside this region.
         *
         * @param point Point to test.
         * @return true if this region contains the given point and false otherwise.
         */
        public boolean containsPoint(Point point) {

            // Test the given point against each edge of the region. Working clockwise,
            // the point is contained if it's to the right of each edge.
            for (int i = 0; i < mCorners.size(); i++) {
                Point point1 = mCorners.get(i);

                int next;
                if (i == mCorners.size() - 1) {
                    next = 0;
                } else {
                    next = i + 1;
                }

                Point point2 = mCorners.get(next);

                // Since we're moving clockwise, a distance < 0 means that
                // the point is to the "left" of this edge and must be outside
                // our region.
                if (distanceToLine(point1, point2, point) < 0) {
                    return false;
                }
            }

            return true;
        }

        /**
         * Create an vertically expanded version of the region. This method is intended to help
         * with calculating overlap of barcodes in the frame. For barcodes that are small vertically
         * in particular this helps to track movement. Several levels of scaling are used depending
         * on the aspect ratio of the bounding box. The closer a bounding box is to square the
         * less scaling will be applied to the region.
         * @return List of points for the expanded region.
         */
        @NonNull
        List<Point> getExpandedPoints()
        {
            List<Point> pts = new ArrayList<>();

            Point pt1 = mCorners.get(0);
            Point pt2 = mCorners.get(1);
            Point pt3 = mCorners.get(2);

            // Get the horizontal & vertical change between points 2 & 3 (points 1 & 2 in the
            // points list). The line between these points represents the height of the region.
            int dX = pt3.x - pt2.x;
            int dY = pt3.y - pt2.y;

            // Calculate the overall width & height of the region. These values are the length of
            // the line segments between the sets of points.
            double width = distanceToPoint( pt1, pt2 );
            double height = distanceToPoint( pt2, pt3 );

            // Get the angle of the barcode vertical direction. This angle is perpendicular to the
            // barcode width. In a 1D barcode this angle could also be thought of as the angle
            // between 0 degrees and a line in the barcode.
            double angle = Math.atan2( dY, dX );

            // Calculate the aspect ratio for the bounding box
            double codeScale = height / width;

            float expansionFactor;

            // Using the aspect ratio determine how much we will enlarge the bounding box height.
            // The expansion factor here is a multiplier that will be applied to the box's height
            // in each direction
            if ( codeScale < Small_Code_Threshold )             // shelf edge code - shorter than 2/10
                expansionFactor = Expansion_Small;
            else if ( codeScale < Large_Code_Threshold )        // medium size code (Target 4) - shorter than 4/10
                expansionFactor = Expansion_Medium;
            else
                expansionFactor = Expansion_Large;              // Normal size code, 4/10 or larger

            // Calculate how much will be added to the top and bottom of the box
            double verticalOffset = expansionFactor * height;

            // Using trig funtions calculate the X & Y values to add to each point
            int xOffset = (int) Math.round(verticalOffset * Math.cos(angle));
            int yOffset = (int) Math.round(verticalOffset * Math.sin(angle));

            // Create the new points. The first two points move up (i.e. factor is -1) while the
            // second two points move down (factor is 1)
            for ( int i = 0; i < 4; i++ )
            {
                // The first two points will move up the second two will move down
                int factor = i < 2 ? -1 : 1;
                Point pt = mCorners.get( i );

                // Create the new point based on the original point and the adjustment values
                pts.add( new Point( pt.x + factor * xOffset, pt.y + factor * yOffset ) );
            }

            return pts;
        }

        // Returns the distance of a point relative to a line. The sign of the
        // result can be used to find what "side" of the line the point lies.
        private int distanceToLine(Point point1, Point point2, Point testPoint) {
            // Determine distance D using the line equation Ax + By + C = 0:
            // D = (x2 - x1) * (yp - y1) - (xp - x1) * (y2 - y1)
            return (point2.x - point1.x) * (testPoint.y - point1.y) - (testPoint.x - point1.x) * (point2.y - point1.y);
        }

        int distanceToPoint( Point point1, Point point2 )
        {
            int dx = point1.x - point2.x;
            int dy = point1.y - point2.y;

            return (int) Math.sqrt( dx * dx + dy * dy );
        }

        private Point centerPoint(List<Point> points) {
            int cx = 0;
            int cy = 0;

            int numberOfPoints = points.size();

            for (int i = 0; i < numberOfPoints; i++) {
                cx += points.get(i).x;
                cy += points.get(i).y;
            }

            cx /= numberOfPoints;
            cy /= numberOfPoints;

            return new Point(cx, cy);
        }
    }
}

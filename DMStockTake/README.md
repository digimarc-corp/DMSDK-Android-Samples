DMStockTake
===========

DMStockTake is a demo app to demonstrate how `TraditionalBarcodeReadDistance` in DMSDK can be utilized in a "stock-taking" scenario where greater scanning distances are needed. The scanned values are displayed with a colored background over the corresponding barcode. The app is configured to scan UPC-A, EAN-13, ITF-14 and Code 128 barcodes, which are commonly used in a stock-taking scenario. 

The app performs additional processing to "smooth out" the visualization of the barcode values and locations on screen. Individual barcodes are tracked across read operations. This data is used to "fill in" results for barcodes that aren't read in one frame or another, which prevents results from flickering in and out. 

The app also demonstrates the use of read regions to limit the image area used for detection. Click the brackets icon on the toolbar to cycle through several different region configurations. Use of a region can help the user read the correct barcode when there are several in the field of view. This is especially useful when scanning from a distance in either Far or Full Range mode.
 
A Settings page is available from the main interface which allows the user to change the read distance.

- Note: Watch a [video demo of DM Stock Take][1]

## Prerequisites

You'll need a valid evaluation or commercial license key to use the core features of the app. Log in to your Digimarc Barcode Manager account to obtain your existing evaluation or commercial license key (https://portal.digimarc.net/). If your evaluation license is expired, please contact sales@digimarc.com to discuss obtaining a commercial license key.

## Configuring Read Distances in DM SDK

The `TraditionalBarcodeReadDistance` switch does several things in DM SDK under the covers. The list below lays out  the features of each distance reading mode.

`Distance_Near`

- Best reading performance in the 3" to 12" range
- Up to 10 barcodes decoded per frame

`Distance_Far`

- Best reading performance in the 12" to 48" range
- Up to 40 barcodes decoded per frame
- Uses 4K camera resolution (3840x2160)

An additional distance mode called `Distance_FullRange` is also available which combines the features of `Distance_Near` and `Distance_Far` modes. Its usage is not recommended in most use cases though due to the higher processing power required.

Read distance is set by passing a ReaderOption object containing a `TraditionalBarcodeReadDistance` entry to the application's `VideoCaptureReader` or `ImageReader`. This can be done during the creation of the reader or by calling the following method on the reader:

```
public void setReaderOptions( @Nullable ReaderOptions options ) throws ReaderException
```

## Result Smoothing

Result smoothing is performed by the `ReadResultCache.java` class in DMStockTake. For each frame of results from the `VideoCaptureReader` the methods listed below are called. `addReadRecord` is called for each read within the `ReaderResult` set: 

- `public void startFrame()`
- `public void addReadRecord( @NonNull final Payload payload, @NonNull final DataDictionary metadata, int rotation )`
- `public void endFrame()`

After `endFrame()` is called the following methods can be used to retrieve data from the cache:

- `public List<ReadData> getCurrentResults()`
- `public List<ReadData> getNewResults()`
- `public List<ReadData> getRemovedResults()`

The separate results lists are provided for ease of tracking or animation of the barcode locations. DMStockTake does not animate barcode locations, so it only uses the `getCurrentResults()` method.

The cache will preserve barcodes for 0.5 seconds after their last read before removing them from the current results list. This timeout value is defined in the constant `Max_Interval` and can be adjusted if desired.

## Getting started

1. Open the project with Android Studio
1. Replace the license placeholder in src/main/res/values/strings.xml file with your license key. 
1. Run the app on a connected device using Android Studio or, alternatively, invoke gradle directly using ```./gradlew installDebug```.


[1]:    https://vimeo.com/499261374
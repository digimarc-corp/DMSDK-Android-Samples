# DMSDK - Android Sample Apps

## DMSDemo
The `DMSDemo` project showcases the core Digimarc Mobile SDK APIs.

## DM Stock Take
The `DM Stock Take` allows you to scan barcodes from near and far distances. The values are then displayed with a colored background over the corresponding barcode. The app is configured to scan UPC-A, EAN-13, ITF-14, and Code 128 barcodes, which are commonly used in a "stock-taking" scenario.

The app performs additional processing to "smooth out" the visualization of the barcode values and locations on screen. Individual barcodes are tracked across read operations. This data is used to "fill in" results for barcodes that aren't read in one frame or another, which prevents a flickering effect.

Toggles to adjust the read distance and visualization smoothing are on the main interface and can be changed while the device is scanning.

- Note: Watch a [video demo of DM Stock Take][1]

## DetectorViewDemo 
This sample app demonstrates usage of the Digimarc DetectorView component, a drop-in solution for mobile content detection of Digimarc Barcode, most common 1D barcodes, and QR codes. 

DetectorViewDemo is written entirely in Kotlin.

[1]:    https://vimeo.com/499261374
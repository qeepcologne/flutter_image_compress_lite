//
// Created by cjl on 2018/9/8.
//

#import "CompressFileHandler.h"
#import "CompressHandler.h"
#import "ExifKeeper.h"

@implementation CompressFileHandler {

}
- (void)handleMethodCall:(FlutterMethodCall *)call result:(FlutterResult)result {

    NSArray *args = call.arguments;
    NSString *path = args[0];
    int minWidth = [args[1] intValue];
    int minHeight = [args[2] intValue];
    int quality = [args[3] intValue];
    int rotate = [args[4] intValue];

    int formatType = [args[6] intValue];
    BOOL keepExif = [args[7] boolValue];

    NSURL *imageUrl = [NSURL fileURLWithPath:path];
    NSData *nsdata = [NSData dataWithContentsOfURL:imageUrl];
    UIImage *img = [UIImage imageWithData:nsdata];

    NSData *data = [CompressHandler compressWithUIImage:img minWidth:minWidth minHeight:minHeight quality:quality rotate:rotate format:formatType];

    if (keepExif) {
        data = [ExifKeeper copyExifFromURL:imageUrl toCompressedData:data];
    }

    result([FlutterStandardTypedData typedDataWithBytes:data]);
}

- (void)handleCompressFileToFile:(FlutterMethodCall *)call result:(FlutterResult)result {
    NSArray *args = call.arguments;
    NSString *path = args[0];
    int minWidth = [args[1] intValue];
    int minHeight = [args[2] intValue];
    int quality = [args[3] intValue];
    NSString *targetPath = args[4];
    int rotate = [args[5] intValue];

    int formatType = [args[7] intValue];
    BOOL keepExif = [args[8] boolValue];

    NSURL *imageUrl = [NSURL fileURLWithPath:path];
    NSData *nsdata = [NSData dataWithContentsOfURL:imageUrl];
    UIImage *img = [UIImage imageWithData:nsdata];

    NSData *data = [CompressHandler compressDataWithUIImage:img minWidth:minWidth minHeight:minHeight quality:quality rotate:rotate format:formatType];

    if (keepExif) {
        data = [ExifKeeper copyExifFromURL:imageUrl toCompressedData:data];
    }

    [data writeToURL:[[NSURL alloc] initFileURLWithPath:targetPath] atomically:YES];

    result(targetPath);
}

@end

#import <Foundation/Foundation.h>

/// Order must match the Dart `CompressFormat` enum so the index is the wire value.
typedef NS_ENUM(NSInteger, ImageCompressFormat) {
    ImageCompressFormatJPEG = 0,
    ImageCompressFormatPNG  = 1,
    ImageCompressFormatHEIC = 2,
    ImageCompressFormatWEBP = 3,
};

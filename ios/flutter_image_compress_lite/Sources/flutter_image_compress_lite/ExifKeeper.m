#import "ExifKeeper.h"

@implementation ExifKeeper

+ (NSDictionary *)exifFromSource:(CGImageSourceRef)source {
    if (!source) return nil;
    CFDictionaryRef properties = CGImageSourceCopyPropertiesAtIndex(source, 0, NULL);
    if (!properties) return nil;
    NSDictionary *dict = (__bridge_transfer NSDictionary *)properties;
    // reset orientation to normal since the image was already rotated during compression
    NSMutableDictionary *mutable = [dict mutableCopy];
    mutable[(__bridge NSString *)kCGImagePropertyOrientation] = @1;
    return mutable;
}

+ (NSData *)applyExif:(NSDictionary *)exif toData:(NSData *)data {
    if (!exif || !data) return data;

    CGImageSourceRef source = CGImageSourceCreateWithData((__bridge CFDataRef)data, NULL);
    if (!source) return data;

    CFStringRef uti = CGImageSourceGetType(source);
    NSMutableData *output = [NSMutableData data];
    CGImageDestinationRef dest = CGImageDestinationCreateWithData((__bridge CFMutableDataRef)output, uti, 1, NULL);
    if (!dest) {
        CFRelease(source);
        return data;
    }

    CGImageDestinationAddImageFromSource(dest, source, 0, (__bridge CFDictionaryRef)exif);
    BOOL ok = CGImageDestinationFinalize(dest);

    CFRelease(dest);
    CFRelease(source);

    return ok ? output : data;
}

+ (NSData *)copyExifFromData:(NSData *)sourceData toCompressedData:(NSData *)compressedData {
    CGImageSourceRef source = CGImageSourceCreateWithData((__bridge CFDataRef)sourceData, NULL);
    NSDictionary *exif = [self exifFromSource:source];
    if (source) CFRelease(source);
    return [self applyExif:exif toData:compressedData];
}

+ (NSData *)copyExifFromURL:(NSURL *)sourceURL toCompressedData:(NSData *)compressedData {
    CGImageSourceRef source = CGImageSourceCreateWithURL((__bridge CFURLRef)sourceURL, NULL);
    NSDictionary *exif = [self exifFromSource:source];
    if (source) CFRelease(source);
    return [self applyExif:exif toData:compressedData];
}

@end

#import <Foundation/Foundation.h>
#import <ImageIO/ImageIO.h>

@interface ExifKeeper : NSObject

+ (NSData *)copyExifFromData:(NSData *)sourceData toCompressedData:(NSData *)compressedData;
+ (NSData *)copyExifFromURL:(NSURL *)sourceURL toCompressedData:(NSData *)compressedData;

@end

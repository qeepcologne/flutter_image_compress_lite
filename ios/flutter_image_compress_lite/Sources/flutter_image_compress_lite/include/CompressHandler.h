#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>
#import "ImageCompressFormat.h"

@interface CompressHandler : NSObject
+ (NSData *)compressWithData:(NSData *)data minWidth:(int)minWidth minHeight:(int)minHeight quality:(int)quality
                      rotate:(int)rotate format:(ImageCompressFormat)format;

+ (NSData *)compressWithUIImage:(UIImage *)image minWidth:(int)minWidth minHeight:(int)minHeight quality:(int)quality
                         rotate:(int)rotate format:(ImageCompressFormat)format;

+ (NSData *)compressDataWithUIImage:(UIImage *)image minWidth:(int)minWidth minHeight:(int)minHeight
                            quality:(int)quality rotate:(int)rotate format:(ImageCompressFormat)format;
@end

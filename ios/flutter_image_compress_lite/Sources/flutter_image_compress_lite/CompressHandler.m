//
// Created by cjl on 2018/9/8.
//

#import "CompressHandler.h"
#import "UIImage+scale.h"
#import "ImageCompressPlugin.h"

@implementation CompressHandler {

}

+ (NSData *)compressWithData:(NSData *)data minWidth:(int)minWidth minHeight:(int)minHeight quality:(int)quality
                      rotate:(int)rotate format:(ImageCompressFormat)format {
    UIImage *img = [[UIImage alloc] initWithData:data];
    return [CompressHandler compressWithUIImage:img minWidth:minWidth minHeight:minHeight quality:quality rotate:rotate format:format];
}

+ (NSData *)compressWithUIImage:(UIImage *)image minWidth:(int)minWidth minHeight:(int)minHeight quality:(int)quality
                         rotate:(int)rotate format:(ImageCompressFormat)format {
    if([ImageCompressPlugin showLog]){
        NSLog(@"width = %.0f",[image size].width);
        NSLog(@"height = %.0f",[image size].height);
        NSLog(@"minWidth = %d",minWidth);
        NSLog(@"minHeight = %d",minHeight);
        NSLog(@"format = %ld", (long)format);
    }

    image = [image scaleWithMinWidth:minWidth minHeight:minHeight];
    if(rotate % 360 != 0){
        image = [image rotate: rotate];
    }
    NSData *resultData = [self compressDataWithImage:image quality:quality format:format];

    return resultData;
}


+ (NSData *)compressDataWithUIImage:(UIImage *)image minWidth:(int)minWidth minHeight:(int)minHeight
                            quality:(int)quality rotate:(int)rotate format:(ImageCompressFormat)format {
    image = [image scaleWithMinWidth:minWidth minHeight:minHeight];
    if(rotate % 360 != 0){
        image = [image rotate: rotate];
    }
    return [self compressDataWithImage:image quality:quality format:format];
}

+ (NSData *)compressDataWithImage:(UIImage *)image quality:(float)quality format:(ImageCompressFormat)format  {
    NSData *data;
    if (format == ImageCompressFormatHEIC) {
        CIImage *ciImage = [CIImage imageWithCGImage:image.CGImage];
        CIContext *ciContext = [[CIContext alloc] initWithOptions:nil];
        NSDictionary *options = @{
            (__bridge NSString *)kCGImageDestinationLossyCompressionQuality: @(quality / 100.0)
        };
        data = [ciContext heifRepresentationOfImage:ciImage
                                              format:kCIFormatARGB8
                                          colorSpace:ciImage.colorSpace
                                             options:options];
    } else if (format == ImageCompressFormatWEBP) {
        // WebP encoding is not supported on iOS; rejected upfront in the Dart validator.
        data = nil;
    } else if (format == ImageCompressFormatPNG) {
        data = UIImagePNGRepresentation(image);
    } else { // ImageCompressFormatJPEG
        data = UIImageJPEGRepresentation(image, (CGFloat) quality / 100);
    }

    return data;
}

@end

#ifndef OpenCVWrapper_h
#define OpenCVWrapper_h

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Keypoint data structure for feature detection results.
 */
@interface CVKeypoint : NSObject
@property (nonatomic, assign) float x;
@property (nonatomic, assign) float y;
@property (nonatomic, assign) float size;
@property (nonatomic, assign) float angle;
@property (nonatomic, assign) float response;
@property (nonatomic, assign) int octave;
@end

/**
 * Feature detection result containing keypoints and descriptors.
 */
@interface CVFeatureResult : NSObject
@property (nonatomic, strong) NSArray<CVKeypoint *> *keypoints;
@property (nonatomic, strong) NSData *descriptors;
@property (nonatomic, assign) int descriptorRows;
@property (nonatomic, assign) int descriptorCols;
@property (nonatomic, assign) int descriptorType;
@end

/**
 * Match result between two keypoints.
 */
@interface CVMatch : NSObject
@property (nonatomic, assign) int queryIdx;
@property (nonatomic, assign) int trainIdx;
@property (nonatomic, assign) float distance;
@end

/**
 * Homography result containing the 3x3 transformation matrix and inliers.
 */
@interface CVHomographyResult : NSObject
@property (nonatomic, strong) NSArray<NSNumber *> *matrix;
@property (nonatomic, strong) NSArray<NSNumber *> *inlierMask;
@property (nonatomic, assign) int inlierCount;
@property (nonatomic, assign) BOOL success;
@end

/**
 * Detector type enumeration.
 */
typedef NS_ENUM(NSInteger, CVDetectorType) {
    CVDetectorTypeORB = 0,
    CVDetectorTypeAKAZE = 1
};

/**
 * OpenCV wrapper for Kotlin/Native interop.
 * Provides feature detection, matching, and homography computation.
 */
@interface OpenCVWrapper : NSObject

/**
 * Check if OpenCV is available.
 */
+ (BOOL)isAvailable;

/**
 * Get OpenCV version string.
 */
+ (NSString *)getVersion;

/**
 * Detect features in an image.
 * @param imageData Raw image data (RGBA format).
 * @param width Image width.
 * @param height Image height.
 * @param detectorType ORB (0) or AKAZE (1).
 * @param maxKeypoints Maximum number of keypoints to detect.
 * @return Feature detection result or nil on error.
 */
+ (nullable CVFeatureResult *)detectFeaturesWithImageData:(NSData *)imageData
                                                    width:(int)width
                                                   height:(int)height
                                             detectorType:(CVDetectorType)detectorType
                                             maxKeypoints:(int)maxKeypoints;

/**
 * Match features between two sets of descriptors.
 * @param descriptors1 First descriptor set.
 * @param rows1 Number of rows in first descriptor set.
 * @param cols1 Number of columns in first descriptor set.
 * @param type1 Type of first descriptors.
 * @param descriptors2 Second descriptor set.
 * @param rows2 Number of rows in second descriptor set.
 * @param cols2 Number of columns in second descriptor set.
 * @param type2 Type of second descriptors.
 * @param ratioThreshold Lowe's ratio test threshold.
 * @return Array of matches or nil on error.
 */
+ (nullable NSArray<CVMatch *> *)matchFeaturesWithDescriptors1:(NSData *)descriptors1
                                                         rows1:(int)rows1
                                                         cols1:(int)cols1
                                                         type1:(int)type1
                                                  descriptors2:(NSData *)descriptors2
                                                         rows2:(int)rows2
                                                         cols2:(int)cols2
                                                         type2:(int)type2
                                                ratioThreshold:(float)ratioThreshold;

/**
 * Compute homography matrix from point correspondences.
 * @param srcPoints Source points (x1,y1,x2,y2,...).
 * @param dstPoints Destination points (x1,y1,x2,y2,...).
 * @param ransacThreshold RANSAC reprojection threshold.
 * @return Homography result or nil on error.
 */
+ (nullable CVHomographyResult *)computeHomographyWithSrcPoints:(NSArray<NSNumber *> *)srcPoints
                                                      dstPoints:(NSArray<NSNumber *> *)dstPoints
                                                ransacThreshold:(double)ransacThreshold;

/**
 * Apply homography transformation to an image.
 * @param imageData Raw image data (RGBA format).
 * @param width Input image width.
 * @param height Input image height.
 * @param homography 3x3 homography matrix as 9-element array.
 * @param outputWidth Output image width.
 * @param outputHeight Output image height.
 * @return Transformed image data or nil on error.
 */
+ (nullable NSData *)warpPerspectiveWithImageData:(NSData *)imageData
                                            width:(int)width
                                           height:(int)height
                                       homography:(NSArray<NSNumber *> *)homography
                                      outputWidth:(int)outputWidth
                                     outputHeight:(int)outputHeight;

@end

NS_ASSUME_NONNULL_END

#endif /* OpenCVWrapper_h */

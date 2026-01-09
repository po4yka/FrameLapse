// IMPORTANT: OpenCV headers MUST be included BEFORE any Apple headers
// to avoid conflicts with Apple's NO macro definition.
#ifdef __cplusplus
#import <opencv2/opencv.hpp>
#import <opencv2/features2d.hpp>
#import <opencv2/calib3d.hpp>
#endif

// The header is shared with KMP cinterop
// Located at: composeApp/src/nativeInterop/cinterop/headers/OpenCVWrapper.h
// A copy is also in iosApp/iosApp/OpenCVWrapper/ for Xcode project reference
#import "OpenCVWrapper.h"

@implementation CVKeypoint
@end

@implementation CVFeatureResult
@end

@implementation CVMatch
@end

@implementation CVHomographyResult
@end

@implementation OpenCVWrapper

+ (BOOL)isAvailable {
    return YES;
}

+ (NSString *)getVersion {
#ifdef __cplusplus
    return [NSString stringWithUTF8String:CV_VERSION];
#else
    return @"unavailable";
#endif
}

+ (nullable CVFeatureResult *)detectFeaturesWithImageData:(NSData *)imageData
                                                    width:(int)width
                                                   height:(int)height
                                             detectorType:(CVDetectorType)detectorType
                                             maxKeypoints:(int)maxKeypoints {
#ifdef __cplusplus
    @try {
        // Create cv::Mat from raw RGBA data
        cv::Mat rgba(height, width, CV_8UC4, (void *)imageData.bytes);

        // Convert to grayscale
        cv::Mat gray;
        cv::cvtColor(rgba, gray, cv::COLOR_RGBA2GRAY);

        // Create detector
        cv::Ptr<cv::Feature2D> detector;
        if (detectorType == CVDetectorTypeORB) {
            detector = cv::ORB::create(maxKeypoints);
        } else {
            detector = cv::AKAZE::create();
        }

        // Detect keypoints and compute descriptors
        std::vector<cv::KeyPoint> keypoints;
        cv::Mat descriptors;
        detector->detectAndCompute(gray, cv::noArray(), keypoints, descriptors);

        // Limit keypoints if using AKAZE (which doesn't have a built-in limit)
        if (detectorType == CVDetectorTypeAKAZE && keypoints.size() > (size_t)maxKeypoints) {
            // Sort by response and keep top maxKeypoints
            std::partial_sort(keypoints.begin(),
                            keypoints.begin() + maxKeypoints,
                            keypoints.end(),
                            [](const cv::KeyPoint& a, const cv::KeyPoint& b) {
                                return a.response > b.response;
                            });
            keypoints.resize(maxKeypoints);

            // Recompute descriptors for remaining keypoints
            detector->compute(gray, keypoints, descriptors);
        }

        // Create result
        CVFeatureResult *result = [[CVFeatureResult alloc] init];

        // Convert keypoints
        NSMutableArray<CVKeypoint *> *keypointsArray = [NSMutableArray arrayWithCapacity:keypoints.size()];
        for (const auto& kp : keypoints) {
            CVKeypoint *cvKp = [[CVKeypoint alloc] init];
            cvKp.x = kp.pt.x;
            cvKp.y = kp.pt.y;
            cvKp.size = kp.size;
            cvKp.angle = kp.angle;
            cvKp.response = kp.response;
            cvKp.octave = kp.octave;
            [keypointsArray addObject:cvKp];
        }
        result.keypoints = keypointsArray;

        // Convert descriptors
        if (!descriptors.empty()) {
            // Ensure descriptors are continuous
            cv::Mat continuousDesc = descriptors.isContinuous() ? descriptors : descriptors.clone();
            result.descriptors = [NSData dataWithBytes:continuousDesc.data
                                               length:continuousDesc.total() * continuousDesc.elemSize()];
            result.descriptorRows = descriptors.rows;
            result.descriptorCols = descriptors.cols;
            result.descriptorType = descriptors.type();
        } else {
            result.descriptors = [NSData data];
            result.descriptorRows = 0;
            result.descriptorCols = 0;
            result.descriptorType = CV_8U;
        }

        return result;
    } @catch (NSException *exception) {
        NSLog(@"OpenCVWrapper: detectFeatures exception: %@", exception);
        return nil;
    }
#else
    return nil;
#endif
}

+ (nullable NSArray<CVMatch *> *)matchFeaturesWithDescriptors1:(NSData *)descriptors1
                                                         rows1:(int)rows1
                                                         cols1:(int)cols1
                                                         type1:(int)type1
                                                  descriptors2:(NSData *)descriptors2
                                                         rows2:(int)rows2
                                                         cols2:(int)cols2
                                                         type2:(int)type2
                                                ratioThreshold:(float)ratioThreshold {
#ifdef __cplusplus
    @try {
        if (descriptors1.length == 0 || descriptors2.length == 0) {
            return @[];
        }

        // Reconstruct cv::Mat from NSData
        cv::Mat desc1(rows1, cols1, type1, (void *)descriptors1.bytes);
        cv::Mat desc2(rows2, cols2, type2, (void *)descriptors2.bytes);

        // Use BFMatcher with Hamming distance for binary descriptors (ORB/AKAZE)
        cv::BFMatcher matcher(cv::NORM_HAMMING);

        // Perform knn matching (k=2 for ratio test)
        std::vector<std::vector<cv::DMatch>> knnMatches;
        matcher.knnMatch(desc1, desc2, knnMatches, 2);

        // Apply Lowe's ratio test
        NSMutableArray<CVMatch *> *matches = [NSMutableArray array];
        for (const auto& matchPair : knnMatches) {
            if (matchPair.size() >= 2) {
                if (matchPair[0].distance < ratioThreshold * matchPair[1].distance) {
                    CVMatch *match = [[CVMatch alloc] init];
                    match.queryIdx = matchPair[0].queryIdx;
                    match.trainIdx = matchPair[0].trainIdx;
                    match.distance = matchPair[0].distance;
                    [matches addObject:match];
                }
            }
        }

        return matches;
    } @catch (NSException *exception) {
        NSLog(@"OpenCVWrapper: matchFeatures exception: %@", exception);
        return nil;
    }
#else
    return nil;
#endif
}

+ (nullable CVHomographyResult *)computeHomographyWithSrcPoints:(NSArray<NSNumber *> *)srcPoints
                                                      dstPoints:(NSArray<NSNumber *> *)dstPoints
                                                ransacThreshold:(double)ransacThreshold {
#ifdef __cplusplus
    @try {
        // Need at least 4 point pairs for homography
        if (srcPoints.count < 8 || dstPoints.count < 8) {
            CVHomographyResult *result = [[CVHomographyResult alloc] init];
            result.success = NO;
            result.matrix = @[];
            result.inlierMask = @[];
            result.inlierCount = 0;
            return result;
        }

        // Convert to cv::Point2f vectors
        std::vector<cv::Point2f> src, dst;
        for (NSUInteger i = 0; i < srcPoints.count; i += 2) {
            src.push_back(cv::Point2f(srcPoints[i].floatValue, srcPoints[i + 1].floatValue));
            dst.push_back(cv::Point2f(dstPoints[i].floatValue, dstPoints[i + 1].floatValue));
        }

        // Compute homography with RANSAC
        cv::Mat mask;
        cv::Mat H = cv::findHomography(src, dst, cv::RANSAC, ransacThreshold, mask);

        if (H.empty()) {
            CVHomographyResult *result = [[CVHomographyResult alloc] init];
            result.success = NO;
            result.matrix = @[];
            result.inlierMask = @[];
            result.inlierCount = 0;
            return result;
        }

        // Convert matrix to NSArray
        NSMutableArray<NSNumber *> *matrixArray = [NSMutableArray arrayWithCapacity:9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                [matrixArray addObject:@(H.at<double>(i, j))];
            }
        }

        // Convert inlier mask
        NSMutableArray<NSNumber *> *inlierMaskArray = [NSMutableArray arrayWithCapacity:mask.rows];
        int inlierCount = 0;
        for (int i = 0; i < mask.rows; i++) {
            BOOL isInlier = mask.at<uchar>(i) != 0;
            [inlierMaskArray addObject:@(isInlier)];
            if (isInlier) inlierCount++;
        }

        CVHomographyResult *result = [[CVHomographyResult alloc] init];
        result.success = YES;
        result.matrix = matrixArray;
        result.inlierMask = inlierMaskArray;
        result.inlierCount = inlierCount;

        return result;
    } @catch (NSException *exception) {
        NSLog(@"OpenCVWrapper: computeHomography exception: %@", exception);
        return nil;
    }
#else
    return nil;
#endif
}

+ (nullable NSData *)warpPerspectiveWithImageData:(NSData *)imageData
                                            width:(int)width
                                           height:(int)height
                                       homography:(NSArray<NSNumber *> *)homography
                                      outputWidth:(int)outputWidth
                                     outputHeight:(int)outputHeight {
#ifdef __cplusplus
    @try {
        if (homography.count != 9) {
            NSLog(@"OpenCVWrapper: warpPerspective invalid homography size: %lu", (unsigned long)homography.count);
            return nil;
        }

        // Create cv::Mat from raw RGBA data
        cv::Mat src(height, width, CV_8UC4, (void *)imageData.bytes);

        // Create 3x3 homography matrix
        cv::Mat H(3, 3, CV_64F);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                H.at<double>(i, j) = homography[i * 3 + j].doubleValue;
            }
        }

        // Apply perspective transformation
        cv::Mat dst;
        cv::warpPerspective(src, dst, H, cv::Size(outputWidth, outputHeight),
                           cv::INTER_LINEAR, cv::BORDER_CONSTANT, cv::Scalar(0, 0, 0, 0));

        // Convert result to NSData
        cv::Mat continuousDst = dst.isContinuous() ? dst : dst.clone();
        return [NSData dataWithBytes:continuousDst.data
                              length:continuousDst.total() * continuousDst.elemSize()];
    } @catch (NSException *exception) {
        NSLog(@"OpenCVWrapper: warpPerspective exception: %@", exception);
        return nil;
    }
#else
    return nil;
#endif
}

@end

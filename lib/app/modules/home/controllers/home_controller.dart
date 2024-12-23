import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:image_picker/image_picker.dart';

class HomeController extends GetxController {
  static const MethodChannel _channel =
      MethodChannel('com.example/image_processor');
  final ImagePicker _picker = ImagePicker();
  final Rx<Uint8List?> processedImage = Rx<Uint8List?>(null);
  final RxMap<String, dynamic> analysisResults = <String, dynamic>{}.obs;

  /// Pick an image from the gallery and send it for processing
  Future<void> pickAndProcessImage() async {
    try {
      final XFile? pickedFile =
          await _picker.pickImage(source: ImageSource.gallery);
      if (pickedFile != null) {
        final Uint8List imageBytes = await pickedFile.readAsBytes();
        await processImage(imageBytes);
      } else {
        Get.snackbar('Error', 'No image selected.');
      }
    } catch (e) {
      Get.snackbar('Error', 'Failed to pick image: $e');
    }
  }

  /// Send the image to the native side for processing
  Future<void> processImage(Uint8List imageBytes) async {
    try {
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
          'analyzeImage', imageBytes);
      if (result != null) {
        processedImage.value = imageBytes; // Display the original image
        analysisResults.value =
            Map<String, dynamic>.from(result); // Display the results
      } else {
        Get.snackbar('Error', 'Image processing failed.');
      }
    } catch (e) {
      Get.snackbar('Error', 'Failed to process image: $e');
    }
  }
}

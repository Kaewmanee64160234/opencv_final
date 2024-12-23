import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:image_picker/image_picker.dart';

class HomeController extends GetxController {
  static const platform = MethodChannel('com.example/native');

  Future<void> navigateToNativePage() async {
    try {
      // Call the native method to navigate
      await platform.invokeMethod('goToNativePage');
    } on PlatformException catch (e) {
      print("Failed to navigate to native page: '${e.message}'.");
    }
  }
}

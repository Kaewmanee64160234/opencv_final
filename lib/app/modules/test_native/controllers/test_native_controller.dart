import 'package:flutter/services.dart';
import 'package:get/get.dart';

class TestNativeController extends GetxController {
  //TODO: Implement TestNativeController
  static const platform = MethodChannel('com.example.native_link');
  final count = 0.obs;
  @override
  void onInit() {
    super.onInit();
  }

  @override
  void onReady() {
    super.onReady();
  }

  @override
  void onClose() {
    super.onClose();
  }

// openNativePage
  Future<void> openNativePage(String message) async {
    try {
      await platform.invokeMethod('openNativePage', message);
    } catch (e) {
      Get.snackbar('Error', 'Failed to open native page: $e');
      print(e);
    }
  }
}

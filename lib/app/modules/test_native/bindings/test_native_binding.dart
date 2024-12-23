import 'package:get/get.dart';

import '../controllers/test_native_controller.dart';

class TestNativeBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<TestNativeController>(
      () => TestNativeController(),
    );
  }
}

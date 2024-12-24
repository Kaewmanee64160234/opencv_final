import 'package:get/get.dart';

import '../controllers/tset_reg_controller.dart';

class TsetRegBinding extends Bindings {
  @override
  void dependencies() {
    Get.lazyPut<TsetRegController>(
      () => TsetRegController(),
    );
  }
}

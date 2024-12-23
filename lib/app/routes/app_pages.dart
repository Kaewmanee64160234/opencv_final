import 'package:get/get.dart';

import '../modules/home/bindings/home_binding.dart';
import '../modules/home/views/home_view.dart';
import '../modules/test_native/bindings/test_native_binding.dart';
import '../modules/test_native/views/test_native_view.dart';

part 'app_routes.dart';

class AppPages {
  AppPages._();

  static const INITIAL = Routes.HOME;

  static final routes = [
    GetPage(
      name: _Paths.HOME,
      page: () => const HomeView(),
      binding: HomeBinding(),
    ),
    GetPage(
      name: _Paths.TEST_NATIVE,
      page: () => const TestNativeView(),
      binding: TestNativeBinding(),
    ),
  ];
}

import 'package:flutter/material.dart';

import 'package:get/get.dart';

import '../controllers/test_native_controller.dart';

class TestNativeView extends GetView<TestNativeController> {
  const TestNativeView({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text("Flutter to Native Link")),
      body: Center(
        child: ElevatedButton(
          onPressed: () => controller.openNativePage("Hello from Flutter!"),
          child: const Text("Open Native Page"),
        ),
      ),
    );
  }
}

import 'package:final_opencv/app/routes/app_pages.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import '../controllers/home_controller.dart';

class HomeView extends GetView<HomeController> {
  const HomeView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(title: const Text('Image Processing')),
        body: Center(
          child: Column(
            children: [
              ElevatedButton(
                  onPressed: controller.navigateToNativePage,
                  child: Text(
                    'Navigate to Native Page',
                    style: TextStyle(fontSize: 20),
                  )),
              ElevatedButton(
                onPressed: () => Get.offNamed(Routes.TSET_REG),
                child: Text('Navigate to Test Reg'),
              ),
            ],
          ),
          // child: Obx(() {
          //   return Column(
          //     mainAxisAlignment: MainAxisAlignment.center,
          //     children: [
          //       if (controller.processedImage.value == null)
          //         const Text('No image selected.'),
          //       if (controller.processedImage.value != null)
          //         Image.memory(controller.processedImage.value!, height: 200),
          //       if (controller.analysisResults.isNotEmpty) ...[
          //         const SizedBox(height: 20),
          //         const Text('Analysis Results:',
          //             style: TextStyle(fontWeight: FontWeight.bold)),
          //         ...controller.analysisResults.entries.map((entry) => ListTile(
          //               title: Text(entry.key),
          //               subtitle: Text(entry.value.toString()),
          //             )),
          //       ],
          //       const SizedBox(height: 20),
          //       ElevatedButton(
          //         onPressed: controller.pickAndProcessImage,
          //         child: const Text('Pick and Analyze Image'),
          //       ),

          // ],
        ));
  }
}

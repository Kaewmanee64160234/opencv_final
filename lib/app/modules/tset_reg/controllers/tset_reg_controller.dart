import 'package:camera/camera.dart';
import 'package:get/get.dart';

class TsetRegController extends GetxController {
  //TODO: Implement TsetRegController

  late CameraController cameraController;
  var isCameraInitialized = false.obs;

  @override
  void onInit() {
    super.onInit();
    initializeCamera();
  }

  void initializeCamera() async {
    try {
      final cameras = await availableCameras();
      if (cameras.isNotEmpty) {
        cameraController = CameraController(cameras[0], ResolutionPreset.high);
        await cameraController.initialize();
        isCameraInitialized.value = true;
      }
    } catch (e) {
      print("Error initializing camera: $e");
    }
  }

  @override
  void onClose() {
    cameraController.dispose();
    super.onClose();
  }

  @override
  void onReady() {
    super.onReady();
  }
}

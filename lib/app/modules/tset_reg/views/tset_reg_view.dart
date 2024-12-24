import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../controllers/tset_reg_controller.dart';

class TsetRegView extends GetView<TsetRegController> {
  const TsetRegView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Stack(
        children: [
          // Overlay UI
          Align(
            alignment: Alignment.center,
            child: Container(
              height: MediaQuery.of(context).size.height * 0.35,
              width: MediaQuery.of(context).size.width * 0.95,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                borderRadius: const BorderRadius.all(Radius.circular(10)),
                border: Border.all(
                  color: Colors.grey, // Border color
                  width: 2,
                ),
              ),
              child: Stack(
                children: [
                  // Left side: CircleAvatar and vertical container
                  Positioned(
                    top: 5.0,
                    left: 5.0,
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.start,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 50,
                          height: 50,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: Colors.grey, // Border for the CircleAvatar
                              width: 2,
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                        Container(
                          width: 30,
                          height: MediaQuery.of(context).size.height * 0.23,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(5),
                            border: Border.all(
                              color: Colors
                                  .grey, // Border for the vertical container
                              width: 2,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  // Bottom-right container with rectangle
                  Positioned(
                    bottom: 20,
                    right: 6,
                    child: Container(
                      width: MediaQuery.of(context).size.width * 0.25,
                      height: MediaQuery.of(context).size.height * 0.15,
                      decoration: BoxDecoration(
                        borderRadius: BorderRadius.circular(5),
                        border: Border.all(
                          color: Colors
                              .grey, // Border for the bottom-right rectangle
                          width: 2,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

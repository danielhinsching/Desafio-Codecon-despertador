# Modelo do detector (desafio da escova)

- Arquivo: `app/src/main/assets/efficientdet_lite0.tflite` (4,4 MB, int8, entrada 320x320, treinado no COCO; contém a classe `toothbrush`).
- Origem: https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite (modelo oficial do MediaPipe Tasks).
- SHA-256: `0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb`
- Licença: Apache-2.0 (EfficientDet-Lite0, TensorFlow/Google; declarada no model card https://huggingface.co/litert-community/efficientdet). A página do MediaPipe não declara licença explícita para o arquivo.
- Uso: só local, sem rede. Biblioteca: `com.google.mediapipe:tasks-vision` (Apache-2.0).

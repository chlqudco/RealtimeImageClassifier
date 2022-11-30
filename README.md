## 설명 ##
- TFLite와 사용자의 카메라를 이용한 실시간 이미지 분류기 앱입니다.
- BookImageClassifier와 동일한 모델이며 1000개의 오브젝트를 분류할 수 있습니다.
- 실시간 화면을 받아오는 데에는 Camera2 API를 사용했으며 쓰레드를 사용합니다.
- 작동 방식은 BookImageClassifier과 동일합니다. 이미지를 Bitmap형식으로 받아온 뒤 Classifier에 넘기면, ARGB8888과 모델의 입력 사이즈에 맞게 변환한 뒤 각 클래스별 확률값을 반환합니다. 이중 가장 높은 값을 사용자에게 노출시킵니다.

## 예시 ##
- ![KakaoTalk_20221130_233214570](https://user-images.githubusercontent.com/68932465/204823166-4deb0345-f6ed-4c5a-8b74-b3ef0976e4a8.gif)

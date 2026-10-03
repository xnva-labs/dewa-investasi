# Face recognition — how it works and what is still needed

## Pipeline (all on-device)
1. **Detection** — ML Kit Face Detection (model bundled with the library). Landmarks, contours and the
   smile/eyes-open classifiers are disabled on purpose: the app must not infer emotion or other attributes.
2. **Embedding** — a TFLite model at `app/src/main/assets/face_embedding.tflite`.
   **This file is not in the repo.** Without it the app detects faces but cannot name anyone, and says so.
   Requirements: float32 input `[1,H,W,3]` scaled `(x-127.5)/128` (RGB), output `[1,N]` embedding
   (e.g. a MobileFaceNet-style model, 112×112, 128/192/512 dims). Check the model's license before shipping.
3. **Matching** — cosine similarity against encrypted templates. A person is "recognized" only if the best
   score ≥ threshold **and** leads the runner-up by ≥ margin; otherwise the result is "Tidak dikenal".

## Consent and data
- Face Recognition is OFF by default. Enrollment needs the toggle ON, Camera ON, and an explicit tick that the
  person agreed.
- Only normalized embedding vectors are stored (Android Keystore AES-GCM via `SecureStorage`). Photos are never
  saved or uploaded; names and vectors are never logged.
- Turning the toggle OFF stops processing and deletes all templates. Per-person and delete-all are in *Data Wajah*.

## Must be done before claiming accuracy
- `FaceMath.DEFAULT_THRESHOLD = 0.60` and `DEFAULT_MARGIN = 0.05` are **starting values, not tested numbers**.
  Calibrate with your model on real photos (same person vs different people, glasses-camera quality,
  low light, angles, masks) and pick a threshold with an acceptable false-accept rate.
- Verify the roll-alignment sign in `FaceEngine.embed()` with a tilted face on a real device.
- If the model ships in the APK, check native-library 16 KB page alignment before the Play Store step.

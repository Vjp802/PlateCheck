sh
gcloud firebase test android run \
  --type robo \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --device model=pixel8,version=34,locale=en,orientation=portrait \
  --project plate-check-15c9b
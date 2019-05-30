default:
	mkdir -p build/gen;
	aapt package -f -m -J build/gen -S res -M AndroidManifest.xml -I /usr/lib/android-sdk/platforms/android-23/android.jar;
	javac -Xlint:deprecation -source 1.7 -target 1.7 -bootclasspath "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar" -classpath "/usr/lib/android-sdk/platforms/android-23/android.jar" -d build/obj build/gen/com/chessclock/android/R.java src/com/chessclock/android/*.java;
	mkdir -p build/apk;
	/usr/lib/android-sdk/build-tools/debian/dx --dex --output=build/apk/classes.dex build/obj/;
	aapt package -f -M AndroidManifest.xml -S res/ -I /usr/lib/android-sdk/platforms/android-23/android.jar -F build/ChessClock.unsigned.apk build/apk/;
	zipalign -f -p 4 build/ChessClock.unsigned.apk build/ChessClock.aligned.apk;
	apksigner sign --ks keystore.jks --ks-key-alias androidkey --ks-pass pass:android --key-pass pass:android --out build/ChessClock.apk build/ChessClock.aligned.apk;

install:
	adb install -r build/ChessClock.apk;

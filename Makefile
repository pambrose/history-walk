server:
	./gradlew backendRun

client:
	./gradlew frontendRun

clean:
	./gradlew clean

versioncheck:
	./gradlew dependencyUpdates

depends:
	./gradlew dependencies

upgrade-wrapper:
	./gradlew wrapper --gradle-version=7.1.1 --distribution-type=bin
default: versioncheck

server:
	./gradlew backendRun

client:
	./gradlew frontendRun

clean:
	./gradlew clean

jar: clean
	./gradlew jar

versioncheck:
	./gradlew dependencyUpdates

depends:
	./gradlew dependencies

dbinfo:
	./gradlew flywayInfo

dbclean:
	./gradlew flywayClean

dbmigrate:
	./gradlew flywayMigrate

dbreset: dbclean dbmigrate

dbvalidate:
	./gradlew flywayValidate

upgrade-wrapper:
	./gradlew wrapper --gradle-version=7.3.3 --distribution-type=bin
default: versioncheck

server:
	./gradlew jvmRun -t

client:
	./gradlew jsRun -t

clean:
	./gradlew clean

build:
	./gradlew build -xtest

jar: clean
	./gradlew jar

yarn-unlock:
	./gradlew kotlinUpgradeYarnLock

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

restart:
	heroku ps:restart

log:
	heroku logs --tail

upgrade-wrapper:
	./gradlew wrapper --gradle-version=8.5 --distribution-type=bin

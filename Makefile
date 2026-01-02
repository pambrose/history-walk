default: versioncheck

server:
	./gradlew jvmRun -t

client:
	./gradlew jsRun -t

clean:
	./gradlew clean

build: clean
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

creds:
	heroku pg:credentials:url DATABASE

restart:
	heroku ps:restart

log:
	heroku logs --tail

upgrade-wrapper:
	./gradlew wrapper --gradle-version=9.2.1 --distribution-type=bin

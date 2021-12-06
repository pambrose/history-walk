# Helpful Links

Creating a Docker Postgres instance: https://hackernoon.com/dont-install-postgres-docker-pull-postgres-bee20e200198

## Instance Creation

```shell
docker run --rm --name pg-docker -e POSTGRES_PASSWORD=docker -d -p 5432:5432 -v $HOME/docker/volumes/history:/var/lib/postgresql/data  postgres
```

## Database Setup

```shell
% psql -h localhost -U postgres -d postgres Password: docker 
psql> create database historywalk;
```
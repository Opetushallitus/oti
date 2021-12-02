# Opetushallinnon tutkintoon ilmoittautuminen

A system for registering to an examination session and keeping records of student results and payments.

## Technologies & Frameworks

Below is non-exhaustive list of the key technologies & frameworks used in the project.

- Java 11
- Node
- Clojure
- Leiningen
- Ragtime
- Duct
- Re-frame
- PostgreSQL

## Developing

### Setup

See DEVENV.md file for instructions for setting up the local development environment.

### Configuration

The application uses [Duct framework](https://github.com/duct-framework/duct) which is based on
[Component framework](https://github.com/stuartsierra/component). This means, that the application configuration and
component dependencies are managed in external edn files.

The main configuration file is /resources/oti/system.edn. This contains configuration shared by all environments.
This configuration is augmented (and overwritten) by environment specific config files. For local development
environment these include /dev/resources/dev.edn and /dev/resources/local.edn (which is ignored by git).

Server configuration is created during deploy by filling the variables in the Ansible template
/oph-configuration/config.edn.template.

URLs for accessing other OPH services are stored in /resources/oti/oti_url.properties.

### Localisation

The registration / participant app supports Finnish and Swedish. The translated strings are fetched from OPH
localisation service on application start-up. Doing a GET request to /oti/api/participant/translations/refresh
will reload the translations from the service.

The category for the strings is "oti".

### Email templates

The application sends HTML emails through OPH messaging service. The emails are constructed from templates located in
/resources/oti/email-templates. Localised email subjects are in a Clojure map in the file email-subjects.edn. This file
must contain a valid data structure in the following format:

    {:some-email-template-id {:fi "Subject in Finnish"
                              :sv "Subject in Swedish"}
     :another-template-id    {:fi "Subject in Finnish"
                              :sv "Subject in Swedish"}}

Base template used for all emails is in the file email-base.html. Template specific HTML markup is in files named after
the template id and language, e.g. some-email-template-id.fi.html. Email construction is handled in the namespace
oti.service.email-templates.

The messaging service will strip any `<style>` elements and links to external stylesheets (which aren't supported by
all email services anyway), so styling has to be done inline in element attributes.

### Migrations

Migrations are handled by [ragtime][]. Migration files are stored in
the `resources/migrations` directory, and are applied in alphanumeric
order.

To update the database to the latest migration, open the REPL and run:

```clojure
dev=> (migrate)
Applying 20150815144312-create-users
Applying 20150815145033-create-posts
```

To rollback the last migration, run:

```clojure
dev=> (rollback)
Rolling back 20150815145033-create-posts
```

Note that the system needs to be setup with `(init)` or `(go)` before
migrations can be applied.

[ragtime]: https://github.com/weavejester/ragtime

### Generators

This project has several generator functions to help you create files.

To create a new endpoint:

```clojure
dev=> (gen/endpoint "bar")
Creating file src/foo/endpoint/bar.clj
Creating file test/foo/endpoint/bar_test.clj
Creating directory resources/foo/endpoint/bar
nil
```

To create a new component:

```clojure
dev=> (gen/component "baz")
Creating file src/foo/component/baz.clj
Creating file test/foo/component/baz_test.clj
nil
```

To create a new boundary:

```clojure
dev=> (gen/boundary "quz" foo.component.baz.Baz)
Creating file src/foo/boundary/quz.clj
Creating file test/foo/boundary/quz_test.clj
nil
```

To create a new SQL migration:

```clojure
dev=> (gen/sql-migration "create-users")
Creating file resources/foo/migrations/20160519143643-create-users.up.sql
Creating file resources/foo/migrations/20160519143643-create-users.down.sql
nil
```

### Keep dependencies up-to-date

The project includes the lein-ancient plugin for finding outdated dependencies.

Find out which deps are outdated by running:

```
lein ancient
```

Then, carefully upgrade outdated dependencies.

## Deploying

Commits pushed to master and develop branches will cause a build to happen in
[Bamboo](https://bamboo.oph.ware.fi/browse/OTI-OB). A successful build can be deployed to the different environments
via the Bamboo GUI.

### Health check

OTI provides two URLs for checking the application status. The path /oti/version will show the build number
and the git commit revision of the running application. The path /oti/health will check that the database connection
is working, and if so, respond with OK.

## Maintenance tips for test environment

Removing all participants and their registrations and payments from database:

```
delete from registration_exam_content_section;
delete from registration_exam_content_module;
delete from payment;
delete from registration;
delete from accredited_exam_section;
delete from accredited_exam_module;
delete from email;
delete from participant;
```

Removing all exam sessions from database:

```
delete from exam_session;
```

## License

Copyright © 2016 The Finnish National Board of Education - Opetushallitus

This program is free software: Licensed under the EUPL, Version 1.1 or - as soon as they will be
approved by the European Commission - subsequent versions of the EUPL (the "Licence");

You may not use this work except in compliance with the Licence. You may obtain a copy of the
Licence at: http://www.osor.eu/eupl/

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the European Union Public Licence for more details.

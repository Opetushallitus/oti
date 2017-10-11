## Local Development Environment

### 1. Set up local database

For convenience, use Docker:
```
docker run --name postgres-oti -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin -p 5432:5432 -d postgres
```

Connect with psql:

```
psql -h localhost -U admin -W
```

Create oti database:
```
create database oti;
```

That's it.

### 2 Set up application

The first time you clone this repository, run
```
lein setup
```

This will create files for local configuration, and prep your system for the project. BEWARE: only run this once. If you run this again, your local configuration (which will be setup next) will be wiped.

#### Local configuration

Next, configure your local configuration.Add this to dev/resources/local.edn
```
{:config {:db {:uri "jdbc:postgresql://localhost/oti"
               :username "admin"
               :password "admin"}
          :ldap {:server "localhost"
                 :port 31337
                 :userdn <removed>
                 :password <removed>
                 :ssl false}
          :authentication {:oti-login-success-uri "http://localhost:3000/oti/auth/cas"}
          :cas {:user {:username <removed> :password <removed>}}
          :paytrail-payment {:oti-paytrail-uri "https://oti.local/oti/paytrail"}}}
```
OTI needs access to certain services in a live test environment. Replace removed usernames and passwords with the corresponding values for the specific environment.

Add the following code to dev/src/local.clj:
```
(ns local
  (:require [figwheel-sidecar.repl-api :as ra]))

(defn start-fw []
  (ra/start-figwheel! "dev"))

(defn stop-fw []
  (ra/stop-figwheel!))
```
You will use these later.

In order to test registration locally, you need a proxy and some configuration for redirecting back to oti after succesfull payment to work.

First, add this to your etc/hosts file:
```
127.0.0.1       oti.local
```

Notice, that we are using this hostname in our configuration above.

The development proxy is a nodejs application and can be found in tools/dev-proxy directory. It needs a self-signed certificate to work, so create one in that same directory:
```
./create-ss-cert.sh
```
Install dependencies with npm:
```
npm install
```

That's it. Your local environment is now configured and you can proceed to starting the application.

#### Start the application

Before you start the application, the following things need to be done:

1. Start your database if not started
2. Start the development proxy (for paytrail return url's to work):
```
cd tools/dev-proxy && sudo node proxy.js
```
You need to start the proxy as root, because it needs to be able to bind to port 80.

3. Start an ssh tunnel for LDAP:
```
ssh -L31337:<ldap-address>:<ldap-port> <username>@<jump-machine-hostname>
```
This will bind the ldap service to localhost:31337, which was configured earlier in local.edn.

That's the prerequisities. Now you're ready to start the application.

To start the local server and start developing, start a REPL:

```sh
lein repl
```

Then load the development environment.

```clojure
user=> (dev)
:loaded
```

Run `go` to initiate and start the system.

```clojure
dev=> (go)
:started
```
By default this creates a web server at <http://localhost:3000>.

When you make changes to your source files, use `reset` to reload any
modified files and reset the server.

```clojure
dev=> (reset)
:reloading (...)
:resumed
```

#### Run database migrations

At this point it's wise to run the database migrations:
```clojure
dev=> (migrate)
Applying 01-some-migration
Applying 02-some-other-migration
etc.
```

#### Compile front-end code and watch for changes using figwheel:

For traditional approach, run
```
lein figwheel
```

Or even better, use our functions defined in local.clj, to run it in our existing repl session:
```
dev=> (local/start-fw)
```
And to stop it:
```
dev=> (local/stop-fw)
```

Now you should have a fully operational OTI system running locally. Happy coding!

(These instructions should still be verified, tested and merged to README.md)

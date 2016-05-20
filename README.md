# Clubby - Clubhouse reporting / API interaction. 

Clubby is a reporting web application for the [Clubhouse](https://clubhouse.io/) software team management tool. It
provides some iteration and roll-up views that Clubhouse itself doesn't have (yet!)
 
For those who are comfortable coding in Clojure it also provides a set of functions that may help you if you're
writing your own code against the Clubhouse API.

## WARNING

This is something of an example application. I've used this 'for real' but I don't rely on it.
Please don't assume it is necessarily something that will be kept up to date, but if you are looking for
some extra reporting functionality from Clubhouse this might be a useful starting 
point for you (especially if you understand some clojure.)

## USAGE

(!) This is in need of clean-up clarification
 
As of 2016-05-20 Clubby is available as a JAR in clojars. Refer to the `sample-app` directory to 
see ... a sample app. You'll need at least the `project.clj` file to define your own version of the app.
If you don't have `lein` available use the version of that in the sample app, and if you want to deploy
the app as a Docker container grap the Docker file too.

To configure Clubby you need to set the CLUBHOUSE_KEY environment variable. To get such a key, 
go to Clubhouse, settings (the cog), 'Your Account', API tokens. You can set this environment variable 
in the context in which you are running the app, or if you're  a little more savvy with clojure you can 
create a `profiles.clj` file in the project root and set it there.

To run a development Clubby instance from a terminal run `bin/lein ring server` in the sample app. Wait
a few seconds and after starting the app it should launch your default browser pointing to the app 
(at localhost:3000)

The home page of the app (available at '/') should show a list of the projects you have in your Clubhouse instance.
You can then drill down into various project views.

To run in production you have a few options:
* Run `bin/lein with-profile production trampoline run -m clubby.web`. By
default the app will run on port 3001, but you can change that by setting a `PORT`
environment variable.
* If you're more Clojure savvy you can compile an Uberjar or Uberwar and use that.
* Finally you can use the included Dockerfile to build a Docker container and deploy that where you will

I also use various Clubby functions from a REPL.
 
You can further configure Clubby by setting various 'state list' variables. You'll need to do this
in a Clojure profile.

I'd welcome feedback in Github, or on twitter @mikebroberts

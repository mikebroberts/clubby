# Clubby - Clubhouse reporting / API interaction. 

## WARNING

This is something of an example application. I've used this 'for real' but I don't rely on it.
Please don't assume it is necessarily something that will be kept up to date, but if you are looking for
some extra reporting functionality from [Clubhouse](https://clubhouse.io/) this might be a useful starting 
point for you (especially if you understand some clojure.)

## USAGE

At present the best way to use this app is to fork, or download, the repository. To configure Clubby you
need to set the CLUBHOUSE-KEY environment variable. To get such a key, go to Clubhouse, settings (the cog), 
'Your Account', API tokens.

You can set this environment variable in the context in which you are running the app, or if you're 
a little more savvy with clojure you can create a `profiles.clj` file in the project root and set them there.

To run a development Clubby instance from a terminal run `bin/lein ring server`. Wait
a few seconds and after starting the app it should launch your default browser
pointing to the app (at localhost:3000)

To run in production you have a few options:
* Run `bin/lein with-profile production trampoline run -m clubby.web`. By
default the app will run on port 3001, but you can change that by setting a `PORT`
environment variable.
* If running on Amazon Elastic Beanstalk create a generic 'Single Container' docker 
application for the application artifact run the following command, and use the 
resulting zip file: `git archive --format=zip HEAD > clubby.zip`
* If you're more Clojure savvy you can compile an Uberjar or Uberwar and use that.

I also use various Clubby functions from a REPL.
 
You can further configure Clubby by setting various 'state list' variables. You'll need to do this
in a Clojure profile, or just hand-edit the defaults towards the bottom of `src/clubby/core.clj`

I'd welcome feedback in Github, or on twitter @mikebroberts

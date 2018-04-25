(defproject clauth "1.0.0-rc17"
  :description "OAuth2 based authentication library for Ring"
  :url "http://github.com/pelle/clauth"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [crypto-random "1.1.0"]
                 [commons-codec "1.6"]
                 [ring/ring-core "1.2.0"]
                 [cheshire "5.2.0"]
                 [clj-time "0.6.0"]
                 [org.mindrot/jbcrypt "0.3m"]
                 [hiccup "1.0.4"]]

  :profiles {:dev {
                   :dependencies [[ring/ring-jetty-adapter "1.1.0"]
                     [lein-marginalia "0.7.0"]
                     [com.taoensso/carmine "2.2.0"]
                     [cc.qbits/alia "4.0.2"]
                     [hiccup-bootstrap "0.1.0"]]}}
  :clean-non-project-classes true )


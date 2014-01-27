(ns datomic-free.t-core
  (:require [midje.sweet :refer :all]
            [datomic-free.core :as core]
            [clj-http.fake :as fake-http]
            [net.cgrand.enlive-html :as html]))

(def downloads-html (java.io.StringReader.
                     "<html>
                        <body>
                          <a class=\"latest\"></a>
                          <a class=\"other\"></a>
                        </body>
                      </html>"))

(html/deftemplate downloads-template downloads-html [latest other]
  [:a.latest] (html/set-attr :href latest)
  [:a.other] (html/set-attr :href other))

(defn downloads-response [latest-uri other-uri]
  (apply str (downloads-template latest-uri other-uri)))

(defn ok [body] (fn [_] {:status 200 :headers {} :body body}))

(fact "The URI to the latest version of datomic is parsed from HTML"
      (let [response (downloads-response "http://whatever.com/1.0.2"
                                         "http://whatever.com/1.0.1")]
        (fake-http/with-fake-routes
          {"https://my.datomic.com/downloads/free" (ok response)}
          (core/get-latest-datomic-version) => "1.0.2")))

(ns datomic-free.core
  (:require [net.cgrand.enlive-html :as html])
  (:gen-class))

(def ^:dynamic *base-url* "https://my.datomic.com")

(defn fetch-url []
  (html/html-resource (java.net.URI. (str *base-url* "/downloads/free"))))

(defn latest [node]
  (-> node (html/select [:a.latest]) first :attrs :href))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println (str *base-url* (latest (fetch-url)))))

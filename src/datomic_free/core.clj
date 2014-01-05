(ns datomic-free.core
  (:require [net.cgrand.enlive-html :as html])
  (:import (java.nio.file Files Path))
  (:gen-class))

(def ^:dynamic *base-url* "https://my.datomic.com")

(def ^:dynamic *home-path* "~/.datomic-free")
(def ^:dynamic *versions-path* (str *home-path* "/versions"))
(def ^:dynamic *active-path* (str *home-path* "/active"))
(def ^:dynamic *data-path* (str *home-path* "/data"))

(defn fetch-url []
  (html/html-resource (java.net.URI. (str *base-url* "/downloads/free"))))

(defn latest [node]
  (-> node (html/select [:a.latest]) first :attrs :href))

(defn symlink [link target]
  (let [link-path (Path. link)
        target-path (Path. target)]
      (Files/createSymbolicLink link target)))

(defn use-datomic [version]
  (delete *active-path*)  
  (symlink (str *versions-path* "/datomic-free-" version) *active-path*))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println (str *base-url* (latest (fetch-url)))))

(ns datomic-free.core
  (:require [net.cgrand.enlive-html :as html]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :refer [unzip]]
            [clj-http.client :as client]
            [clojure.java.io :refer [copy file]])
  (:import [java.nio.file Files Paths])
  (:gen-class))

(def ^:dynamic *base-url* "https://my.datomic.com")

(def ^:dynamic *home-path* (str (System/getenv "HOME") "/.datomic-free"))
(def ^:dynamic *versions-path* (str *home-path* "/versions"))
(def ^:dynamic *active-path* (str *home-path* "/active"))
(def ^:dynamic *data-path* (str *home-path* "/data"))

(defn fetch-url []
  (html/html-resource (java.net.URI. (str *base-url* "/downloads/free"))))

(defn get-latest-datomic-version []
  (str *base-url* (-> (fetch-url) (html/select [:a.latest]) first :attrs :href)))

(defn download-datomic [version]
  (let [path (str *versions-path* "/datomic-free-" version)]
    (if (fs/exists? path)
      (println (format "Datomic Free %s is already present." version))
      (do
        (fs/mkdirs *versions-path*)
        (let [url (str *base-url* "/downloads/free/" version)
              response (client/get url {:as :stream})
              zip-file "datomic-free.zip"]
          (if (= 200 (:status response))
            (do
              (with-open [i (:body response)]
                (copy i (file (str *versions-path* "/" zip-file))))
              (fs/with-cwd *versions-path*
                (unzip zip-file ".")
                (fs/delete zip-file)))
            (println version "is not a valid version. See https://my.datomic.com/downloads/free for a list of versions.")))))))

(defn symlink [link target]
  (let [link-path (Paths/get link)
        target-path (Paths/get target)]
      (Files/createSymbolicLink link target nil)))

(defn use-datomic [version]
  (fs/delete *active-path*)
  (symlink (str *versions-path* "/datomic-free-" version) *active-path*))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println (get-latest-datomic-version)))

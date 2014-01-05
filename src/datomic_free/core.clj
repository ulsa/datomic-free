(ns datomic-free.core
  (:require [net.cgrand.enlive-html :as html]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :refer [unzip]]
            [clj-http.client :as client]
            [clojure.java.io :refer [copy file]])
  (:gen-class))

(def ^:const HOME (System/getenv "HOME"))
(def ^:const DATOMIC_FREE_HOME (str HOME "/.datomic-free"))
(def ^:const DATOMIC_FREE_VERSIONS (str DATOMIC_FREE_HOME "/versions"))
(def ^:const DATOMIC_FREE_ACTIVE (str DATOMIC_FREE_HOME "/active"))
(def ^:const DATOMIC_FREE_DATA (str DATOMIC_FREE_HOME "/data"))

(def ^:dynamic *base-url* "https://my.datomic.com")

(defn fetch-url []
  (html/html-resource (java.net.URI. (str *base-url* "/downloads/free"))))

(defn get-latest-datomic-version []
  (str *base-url* (-> (fetch-url) (html/select [:a.latest]) first :attrs :href)))

(defn download-datomic [version]
  (let [path (str DATOMIC_FREE_VERSIONS "/datomic-free-" version)]
    (if (fs/exists? path)
      (println (format "Datomic Free %s is already present." version))
      (do
        (fs/mkdirs DATOMIC_FREE_VERSIONS)
        (let [url (str *base-url* "/downloads/free/" version)
              response (client/get url {:as :stream})
              zip-file "datomic-free.zip"]
          (if (= 200 (:status response))
            (do
              (with-open [i (:body response)]
                (copy i (file (str DATOMIC_FREE_VERSIONS "/" zip-file))))
              (fs/with-cwd DATOMIC_FREE_VERSIONS
                (unzip zip-file ".")
                (fs/delete zip-file)))
            (println version "is not a valid version. See https://my.datomic.com/downloads/free for a list of versions.")))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println (get-latest-datomic-version)))

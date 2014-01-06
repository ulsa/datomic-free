(ns datomic-free.core
  (:require [net.cgrand.enlive-html :as html]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :refer [unzip]]
            [clj-http.client :as client]
            [clojure.java.io :refer [copy file]]
            [clojure.string :as string]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.cli :refer [parse-opts]])
  (:import [java.nio.file Files Paths Path]
           [java.nio.file.attribute FileAttribute])
  (:gen-class))

(def ^:dynamic *base-url* "https://my.datomic.com")

(def ^:dynamic *home-path* (str (System/getenv "HOME") "/.datomic-free"))
(def ^:dynamic *versions-path* (str *home-path* "/versions"))
(def ^:dynamic *active-path* (str *home-path* "/active"))
(def ^:dynamic *data-path* (str *home-path* "/data"))

(defn fetch-url []
  (html/html-resource (java.net.URI. (str *base-url* "/downloads/free"))))

(defn get-latest-datomic-version []
  (-> (fetch-url) (html/select [:a.latest]) first :attrs :href (string/split #"/") last))

(defn path-to-version [version]
  (str *versions-path* "/datomic-free-" version))

(defn download-datomic [version]
  (let [path (path-to-version version)]
    (if (fs/exists? path)
      (println (format "Datomic Free %s is already present." version))
      (do
        (fs/mkdirs *versions-path*)
        (printf "Downloading Datomic Free %s...\n" version)
        (let [url (str *base-url* "/downloads/free/" version)
              response (client/get url {:as :stream})
              zip-file "datomic-free.zip"]
          (if (= 200 (:status response))
            (do
              (with-open [i (:body response)]
                (copy i (file (str *versions-path* "/" zip-file))))
              (fs/with-cwd *versions-path*
                (unzip zip-file ".")
                (fs/delete zip-file))
              (make-transactor-executable version))
            (println version "is not a valid version. See https://my.datomic.com/downloads/free for a list of versions.")))))))

(defn download-latest-datomic []
  (println "Finding latest datomic version...")
  (let [version (get-latest-datomic-version)]
    (if (string/blank? version)
      (println "The latest version could not be found. Install a specific version with \"datomic-free update VERSION\".")
      (download-datomic version))))

(defn to-path [p]
  (if (instance? Path p)
    p
    (Paths/get p (into-array String []))))

(defn symlink [link target]
  (Files/createSymbolicLink (to-path link)
                            (to-path target)
                            (into-array FileAttribute [])))

(defn symlink-target [link]
  (-> link (to-path) (Files/readSymbolicLink)))

(defn use-datomic [version]
  (fs/delete *active-path*)
  (symlink *active-path* (str *versions-path* "/datomic-free-" version)))

(defn make-transactor-executable [version]
  (println "Making datomic executable")
  (let [executables ["bin/transactor" "bin/classpath" "bin/maven-install"]
        make-executable #(fs/chmod "+x" (str (path-to-version version) "/" %))]
    (map make-executable executables)))

(defn start-transactor
  ([] (start-transactor (str *active-path* "/config/samples/free-transactor-template.properties")))
  ([config]
   (let [executable (str *active-path* "/bin/transactor")]
     (sh executable config))))

(def cli-options
  [["-h" "--help" "Show help" :default false :flag true]])

(defn usage [options-summary]
  (->> ["Utility for downloading, upgrading and starting Datomic Free."
        ""
        "Usage: lein run -- [options] <start|update|use> [version]"
        "Usage: java -jar datomic-free-<version>-standalone.jar [options] [start|update|use]"
        ""
        "Options:"
        options-summary
        ""
        "Please refer to the README.md for more information."]
       (string/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (string/join \newline errors)))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn -main
  [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
     (:help options) (exit 0 (usage summary))
     (let [n (count arguments)]
       (or (> n 2) (< n 1))) (exit 1 (usage summary))
     errors (exit 1 (error-msg errors)))
    ;; Execute program with options
    (case (first arguments)
      "start"
      (do
        (when-not (fs/exists? *active-path*)
          (println "datomic-free has not been activated yet. datomicizing...")
          (fs/mkdirs *home-path*)
          (download-latest-datomic))
        ;; TODO: add support for specific config
        (start-transactor))
      "update"
      (if-let [version (second arguments)]
        (download-datomic version)
        (download-latest-datomic))
      "use"
      (if-let [version (second arguments)]
        (use-datomic version)
        (println "No version given"))
      (exit 1 (usage summary)))))

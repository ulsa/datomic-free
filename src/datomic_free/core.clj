(ns datomic-free.core
  (:require [net.cgrand.enlive-html :as html]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :refer [unzip]]
            [clj-http.client :as client]
            [clojure.java.io :refer [copy file]]
            [clojure.string :as string]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.stacktrace :refer [print-throwable]])
  (:import java.io.IOException
           [java.nio.file Files Paths Path]
           [java.nio.file.attribute FileAttribute])
  (:gen-class))

(def ^:dynamic *base-url* "https://my.datomic.com")

(def ^:dynamic *home-path* (str (System/getenv "HOME") "/.datomic-free"))
(def ^:dynamic *versions-path* (str *home-path* "/versions"))
(def ^:dynamic *active-path* (str *home-path* "/active"))
(def ^:dynamic *data-path* (str *home-path* "/data"))

(defn- fetch-url []
  (html/html-resource (java.net.URI. (str *base-url* "/downloads/free"))))

(defn get-latest-datomic-version []
  (-> (fetch-url) (html/select [:a.latest]) first :attrs :href (string/split #"/") last))

(defn path-for-version [version]
  (str *versions-path* "/datomic-free-" version))

(defn- to-path [p]
  (if (instance? Path p)
    p
    (Paths/get p (into-array String []))))

(defn- symlink [link target]
  (Files/createSymbolicLink (to-path link)
                            (to-path target)
                            (into-array FileAttribute [])))

(defn- symlink-target [link]
  (-> link (to-path) (Files/readSymbolicLink)))

(defn- copy-stream-to-file [stream zip-file]
  (with-open [i stream]
    (copy i (file (str *versions-path* "/" zip-file)))))

(defn- unzip-and-delete [zip-file]
  (fs/with-cwd *versions-path*
    (unzip zip-file ".")
    (fs/delete zip-file)))

;; TODO make everything in bin executable recursively, using fs/walk and fs/chmod
(defn- make-transactor-executable [version]
  (println "Making datomic executable")
  (doseq [executable ["bin/transactor" "bin/classpath" "bin/maven-install"]
          :let [path (str (path-for-version version) "/" executable)]]
    (when (fs/exists? path)
      (fs/chmod "+x" path))))

(defn- install-maven-artifacts [version]
  (try
    (sh "mvn" "-v")
    (println (format "Installing datomic-free-%s in local maven repository..." version))
    (try
      (let [p (sh "bin/maven-install" :dir (str *versions-path* "/" version))
            status (:exit p)]
        (when-not (zero? status)
          (println (:err p))))
      (catch Exception e
        (println "Problem running Datomic script 'bin/maven-install':") (print-throwable e)))
    (catch IOException e
      (println "No 'mvn' command available, skipping install in local maven repository"))))

(defn use-datomic [version]
  (fs/delete *active-path*)
  (symlink *active-path* (str *versions-path* "/datomic-free-" version)))

(defn update-data-dir [version]
  ;; TODO do stuff here
  )

(defn download-datomic [version]
  (let [path (path-for-version version)]
    (if (fs/exists? path)
      (println (format "Datomic Free %s is already present." version))
      (do
        (fs/mkdirs *versions-path*)
        (println (format "Downloading Datomic Free %s.." version))
        (let [url (str *base-url* "/downloads/free/" version)
              response (client/get url {:as :stream})
              zip-file "datomic-free.zip"]
          (if (= 200 (:status response))
            (do
              (copy-stream-to-file (:body response) zip-file)
              (unzip-and-delete zip-file)
              (make-transactor-executable version)
              (install-maven-artifacts version)
              (use-datomic version)
              (update-data-dir version)
              (println (format "Done. Datomic Free %s is now available." version)))
            (println version "is not a valid version. See https://my.datomic.com/downloads/free for a list of versions.")))))))

(defn download-latest-datomic []
  (println "Finding latest datomic version...")
  (let [version (get-latest-datomic-version)]
    (if (string/blank? version)
      (println "The latest version could not be found. Install a specific version with \"datomic-free update VERSION\".")
      (download-datomic version))))

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

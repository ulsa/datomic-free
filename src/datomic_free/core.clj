(ns datomic-free.core
  (:require [net.cgrand.enlive-html :as html]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :refer [unzip]]
            [clj-http.client :as client]
            [clojure.java.io :as io]
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

(defn- sym-link-target [link]
  (-> link (fs/file) (.toPath) (Files/readSymbolicLink)))

(defn- copy-stream-to-file [stream zip-file]
  (with-open [i stream]
    (io/copy i (io/file (str *versions-path* "/" zip-file)))))

(defn- unzip-and-delete [zip-file]
  (fs/with-cwd *versions-path*
    (unzip zip-file ".")
    (fs/delete zip-file)))

(defn- make-transactor-executable [version]
  (println "Making datomic executable")
  (fs/walk
   (fn [parent _ files] (doseq [file files] (fs/chmod "+x" (io/file parent file))))
   (str (path-for-version version) "/bin")))

(defn- install-maven-artifacts [version]
  (try
    (sh "mvn" "-v")
    (println (format "Installing datomic-free-%s in local maven repository..." version))
    (try
      (let [p (sh "bin/maven-install" :dir (path-for-version version))
            status (:exit p)]
        (when-not (zero? status)
          (println (:err p))))
      (catch Exception e
        (println "Problem running Datomic script 'bin/maven-install':") (print-throwable e)))
    (catch IOException e
      (println "No 'mvn' command available, skipping install in local maven repository"))))

(defn- update-data-dir [version]
  (let [version-data-path (str (path-for-version version) "/data")]
    (when (fs/exists? version-data-path)
      (fs/delete-dir version-data-path))
    (fs/mkdirs *data-path*)
    (fs/sym-link version-data-path *data-path*)))

(defn- update-active-link [version]
  (fs/delete *active-path*)
  (fs/sym-link *active-path* (path-for-version version)))

(defn use-datomic [version]
  (update-active-link version)
  (update-data-dir version)
  (println (format "Done. Datomic Free %s is now available." version)))

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
              (use-datomic version))
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
        "Usage: lein run -- [options] <start|update|use> [arg]"
        "   or: java -jar datomic-free-<version>-standalone.jar [options] <start|update|use> [arg]"
        ""
        "where 'arg' depends on the command:"
        ""
        " - start [config]       (default: config/samples/free-transactor-template.properties)"
        " - update [version]     (default: latest version)"
        " - use [version]        (default: latest version)"
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
        (if-let [config (second arguments)]
          (start-transactor config)
          (start-transactor)))
      "update"
      (if-let [version (second arguments)]
        (download-datomic version)
        (download-latest-datomic))
      "use"
      (if-let [version (second arguments)]
        (use-datomic version)
        (use-datomic (get-latest-datomic-version)))
      (exit 1 (usage summary)))))

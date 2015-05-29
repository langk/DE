(ns porklock.core
  (:gen-class)
  (:use [porklock.commands]
        [porklock.validation]
        [slingshot.slingshot :only [try+ throw+]])
  (:require [clojure.tools.cli :as cli]
            [clojure.string :as string]
            [common-cli.version :as version]
            [clojure-commons.error-codes
             :as error
             :refer [ERR_DOES_NOT_EXIST ERR_NOT_A_FILE ERR_NOT_A_FOLDER ERR_NOT_WRITEABLE]]))


(defn- fmeta-split
  [arg]
  (filter #(not (nil? %)) (string/split arg #",")))

(defn get-settings
  [args]
  (let [file-metadata (atom [])
        fmeta-set     #(reset! file-metadata (conj @file-metadata (fmeta-split %)))]
    (cli/cli
      args
      ["-u"
       "--user"
       "The user the tool should run as."
       :default nil]

      ["-s"
       "--source"
       "The directory in iRODS contains files to be downloaded."
       :default nil]

      ["-d"
       "--destination"
       "The local directory that the files will be downloaded into."
       :default "."]

      ["-c"
       "--config"
       "Tells porklock where to read its configuration."
       :default nil]

      ["-m"
       "--meta"
       "Comma-delimited ATTR-VALUE-UNIT"
       :parse-fn fmeta-set]

      ["-h"
       "--help"
       "Prints this help."
       :flag true])))

(defn put-settings
  [args]
  (let [file-metadata (atom [])
        fmeta-set     #(reset! file-metadata (conj @file-metadata (fmeta-split %)))]
    (cli/cli
      args
      ["-u"
       "--user"
       "The user the tool should run as."
       :default nil]

      ["-e"
       "--exclude"
       "List of files to be excluded from uploads."
       :default ""]

      ["-x"
       "--exclude-delimiter"
       "Delimiter for the list of files to be excluded from uploads"
       :default ","]

      ["-i"
       "--include"
       "List of files to make sure are uploaded"
       :default ""]

      ["-n"
       "--include-delimiter"
       "Delimiter for the list of files that should be included in uploads."
       :default ","]

      ["-s"
       "--source"
       "The local directory containing files to be transferred."
       :default "."]

      ["-d"
       "--destination"
       "The destination directory in iRODS."
       :default nil]

      ["-c"
       "--config"
       "Tells porklock where to read its configuration."
       :default nil]

      ["-m"
       "--meta"
       "Comma-delimited ATTR-VALUE-UNIT"
       :parse-fn fmeta-set]

      ["-p"
       "--skip-parent-meta"
       "Tells porklock to skip applying metadata to the parent directories of an analysis."
       :flag true]

      ["-h"
       "--help"
       "Prints this help."
       :flag true])))

(def usage "Usage: porklock get|put [options]")

(defn command
  [all-args]
  (when-not (> (count all-args) 0)
    (println usage)
    (System/exit 1))

  (when-not (contains? #{"put" "get" "--version"} (first all-args))
    (println usage)
    (System/exit 1))

  (string/trim (first all-args)))

(defn settings
  [cmd cmd-args]
  (try
    (case cmd
      "get"   (get-settings cmd-args)
      "put"   (put-settings cmd-args)
      "--version" []
      (do
        (println usage)
        (System/exit 1)))
    (catch Exception e
      (println (.getMessage e))
      (System/exit 1))))

(defn err-msg
  [err]
  (let [err-code (:error_code err)]
    (cond
      (= err-code ERR_DOES_NOT_EXIST)      (str "Path does not exist: " (:path err))
      (= err-code ERR_NOT_A_FOLDER)        (str "Path is not a folder: " (:path err))
      (= err-code ERR_NOT_A_FILE)          (str "Path is not a file: " (:path err))
      (= err-code ERR_NOT_WRITEABLE)       (str "Client needs write permission on: " (:path err))
      (= err-code "ERR_PATH_NOT_ABSOLUTE") (str "Path is not absolute: " (:path err))
      (= err-code "ERR_BAD_EXIT_CODE")     (str "Command exited with status: " (:exit-code err))
      (= err-code "ERR_ACCESS_DENIED")     "You can't run this."
      (= err-code "ERR_MISSING_OPTION")    (str "Missing required option: " (:option err))
      :else                                (str "Error: " err))))


(defn -main
  [& args]
  (try+
    (println "[porklock] [arguments] " args)

    (let [cmd          (command args)
          version-info (version/version-info "org.iplantc" "porklock")
          cmd-args     (rest args)
          [options remnants banner] (settings cmd cmd-args)]
      (println (str "[porklock] [command] '" cmd "'"))
      (println "[porklock] [options] " options)
      (println "[porklock] [remnants] " remnants)

      (when (= cmd "--version")
        (println version-info)
        (System/exit 0))

      (when-not (> (count cmd-args) 0)
        (println banner)
        (System/exit 1))

      (when (:help options)
        (println banner)
        (System/exit 0))

      (case cmd
        "get"   (do
                  (validate-get options)
                  (iget-command options)
                  (System/exit 0))

        "put"   (do
                  (validate-put options)
                  (iput-command options)
                  (System/exit 0))

        (do
          (println banner)
          (System/exit 1))))
    (catch error/error? err
      (println (err-msg err))
      (System/exit 1))
    (catch Exception e
      (println (error/format-exception e))
      (System/exit 2))))

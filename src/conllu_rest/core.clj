(ns conllu-rest.core
  (:require [conllu-rest.server.config :refer [env]]
            [conllu-rest.server.xtdb :refer [xtdb-node]]
            [conllu-rest.xtdb.creation :refer [ingest-conllu-files]]
            [conllu-rest.server.http]
            [conllu-rest.server.repl]
            [conllu-rest.server.tokens :as tok]
            [clojure.tools.logging :as log]
            [mount.core :as mount]
            [cli-matic.core :refer [run-cmd*]]
            [cli-matic.utils-v2 :as U2]
            [cli-matic.utils :as U]
            [cli-matic.help-gen :as H]
            [cli-matic.platform :as P]
            [conllu-rest.xtdb.easy :as cxe])
  (:gen-class))

;; log uncaught exceptions in threads
(Thread/setDefaultUncaughtExceptionHandler
  (reify Thread$UncaughtExceptionHandler
    (uncaughtException [_ thread ex]
      (log/error {:what      :uncaught-exception
                  :exception ex
                  :where     (str "Uncaught exception on" (.getName thread))}))))

(defn stop-app []
  (doseq [component (:stopped (mount/stop))]
    (log/info component "stopped"))
  (shutdown-agents))

(defn ingest [args]
  (mount/start-with-args args)
  (log/info (:filepaths args))
  (ingest-conllu-files xtdb-node (:filepaths args))
  (log/info (str "Successfully ingested " (count (:filepaths args)) " documents:"))
  (println "\nBegin document manifest:\n")
  (doseq [name (:filepaths args)]
    (println (str "\t- " name)))
  (println "\nEnd document manifest.\n"))

(defn add-token [{:keys [name email] :as args}]
  (mount/start-with-args args)
  (log/info (str "Attemping to add a token for user:\n\n\tName: " name "\n\tEmail: " email "\n"))
  (let [{:keys [secret]} (tok/create-token tok/xtdb-token-node args)]
    (log/info (str "Successfully created token:\n\n\t" secret "\n\nKeep this token SECRET."))))

(defn list-tokens [args]
  (mount/start-with-args args)
  (log/info "Existing tokens:\n")
  (let [records (cxe/find-entities tok/xtdb-token-node {:secret '_})]
    (binding [clojure.pprint/*print-miser-width* 80
              clojure.pprint/*print-right-margin* 100
              clojure.pprint/*print-pprint-dispatch* clojure.pprint/code-dispatch]
      (doseq [record records]
        (println (with-out-str (clojure.pprint/pprint record))))))
  (println))

(defn revoke-token [{:keys [secret] :as args}]
  (mount/start-with-args args)
  (println)
  (log/info (str "Attempting to revoke token " secret))
  (if (some? (tok/read-token tok/xtdb-token-node (keyword secret)))
    (do
      (tok/delete-token tok/xtdb-token-node (keyword secret))
      (log/info "Deletion successful."))
    (log/warn (str "Token does note exist: " secret)))
  (println))

(def cli-config
  {:app         {:command     "conllu-rest"
                 :description "https://github.com/lgessler/conllu-rest"
                 :version     "0.0.1"}
   :global-opts []
   :commands    [;; main method--run the HTTP server
                 {:command     "run"
                  :short       "r"
                  :description ["Start the web app and begin listening for requests."]
                  :opts        [{:option "port" :short "p" :as "port for HTTP server" :type :int}]
                  :runs        mount/start-with-args
                  :on-shutdown stop-app}

                 ;; read in conllu files
                 {:command     "ingest"
                  :short       "i"
                  :description ["Read and ingest CoNLL-U files."
                                ""
                                "NOTE: you should only run this command while your server is shut down."]
                  :opts        [{:option   "filepaths"
                                 :short    0
                                 :as       "paths to CoNLL-U files to ingest"
                                 :type     :string
                                 :multiple true}]
                  :runs        ingest
                  :on-shutdown stop-app}

                 {:command     "token"
                  :short       "t"
                  :description ["Token-related helpers."]
                  :opts        []
                  :subcommands [{:command     "add"
                                 :short       "a"
                                 :description "Mint a new token for a user"
                                 :opts        [{:option "name"
                                                :short  0
                                                :as     "User's name (human-friendly)"
                                                :type   :string}
                                               {:option "email"
                                                :short  1
                                                :as     "User's email"
                                                :type   :string}]
                                 :runs        add-token
                                 :on-shutdown stop-app}
                                {:command     "list"
                                 :short       "l"
                                 :description "List all valid tokens"
                                 :opts        []
                                 :runs        list-tokens
                                 :on-shutdown stop-app}
                                {:command     "revoke"
                                 :short       "r"
                                 :description "Remove a valid token"
                                 :opts        [{:option "secret"
                                                :short  0
                                                :as     "The token to be revoked"
                                                :type   :string}]
                                 :runs        revoke-token
                                 :on-shutdown stop-app}]}]})


(defn run-cmd
  "like cli-matic's run-cmd, but doesn't exit at the end if the command is 'run'"
  [args supplied-config]
  (let [config (U2/cfg-v2 supplied-config)
        {:keys [help stderr subcmd retval] :as result} (run-cmd* config args)]

    ; prints the error message, if present
    (when (seq stderr)
      (U/printErr ["** ERROR: **" stderr "" ""]))

    ; prints help
    (cond
      (= :HELP-GLOBAL help)
      (let [helpFn (H/getGlobalHelperFn config subcmd)]
        (U/printErr (helpFn config subcmd)))

      (= :HELP-SUBCMD help)
      (let [helpFn (H/getSubcommandHelperFn config subcmd)]
        (U/printErr (helpFn config subcmd))))

    ;; For some reason, the run subcommand exits immediately when combined with cli-matic. Use this as a workaround.
    (log/info result)
    (if (and (#{"run" "r"} (first args)) (= retval 0))
      (log/info "Started server successfully")
      (P/exit-script retval))))

(defn start-app [args]
  (run-cmd args cli-config))

(defn -main [& args]
  (start-app args))

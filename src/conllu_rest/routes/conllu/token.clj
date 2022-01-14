(ns conllu-rest.routes.conllu.token
  (:require [ring.util.http-response :refer :all]
            [conllu-rest.common :as common :refer [error-response nyi-response]]
            [conllu-rest.routes.conllu.common :as cc]
            [conllu-rest.xtdb.easy :as cxe]
            [conllu-rest.xtdb.queries.token :as cxqt]
            [spec-tools.data-spec :as ds]))

(defn- ia [colname]
  (if (#{"xpos"} colname)
    "an"
    "a"))

(defn put [id-keyword]
  (fn put [{:keys [path-params body-params node] :as req}]
    (let [id (:id path-params)
          value-keyword (keyword (namespace id-keyword) "value")]
      (if-let [id (common/parse-uuid id)]
        (let [{:keys [status msg]} (cxqt/put node
                                             {id-keyword    id
                                              value-keyword (:value body-params)}
                                             id-keyword)]
          (if (= status :ok)
            (ok)
            (bad-request msg)))
        (bad-request "Sentence ID must be a valid java.util.UUID")))))

(defn delete-assoc [id-keyword]
  (fn delete [{:keys [path-params node] :as req}]
    (let [id (:id path-params)]
      (if-let [id (common/parse-uuid id)]
        (let [{:keys [status msg]} (cxqt/delete-assoc node id id-keyword)]
          (if (= status :ok)
            (ok)
            (bad-request msg)))
        (bad-request "Sentence ID must be a valid java.util.UUID")))))

(defn put-head [{:keys [path-params body-params node] :as req}]
  (let [id (:id path-params)]
    (if-let [id (common/parse-uuid id)]
      (let [{:keys [status msg]} (cxqt/put-head node
                                                {:head/id    id
                                                 :head/value (if (= "root" (:value body-params))
                                                               :root
                                                               (:value body-params))})]
        (if (= status :ok)
          (ok)
          (bad-request msg)))
      (bad-request "Sentence ID must be a valid java.util.UUID"))))

(defn put-deprel [{:keys [path-params body-params node] :as req}]
  (let [id (:id path-params)]
    (if-let [id (common/parse-uuid id)]
      (let [{:keys [status msg]} (cxqt/put-deprel node
                                                  {:deprel/id    id
                                                   :deprel/value (:value body-params)})]
        (if (= status :ok)
          (ok)
          (bad-request msg)))
      (bad-request "Sentence ID must be a valid java.util.UUID"))))

(defn token-routes []
  ["/token"
   ["/id/:id"
    {:get {:summary    "Produce JSON representation of a token"
           :parameters {:path {:id uuid?}}
           :handler    (cc/get-handler :token/id)}
     ;; Scope this out for now--would need tricky special handling e.g. for supertoken/subtoken situations
     #_#_:delete {:summary    "Delete a token"
                  :parameters {:path {:id uuid?}}
                  :handler    nop}}]])

(defn atomic-routes [colname]
  (let [id-keyword (keyword colname "id")]
    [(str "/" colname)
     ["/id/:id"
      {:get {:summary    (str "Produce JSON representation of " (ia colname) " " colname " annotation")
             :parameters {:path {:id uuid?}}
             :handler    (cc/get-handler id-keyword)}
       :put {:summary    (str "Update " (ia colname) " " colname " annotation. Pass a JSON as body with a \"value\" key.")
             :parameters {:path {:id uuid?}
                          :body {:value string?}}
             :handler    (put id-keyword)}}]]))

;; MISC and FEATS
;; Currently unsupported: creating a new one from scratch
(defn associative-routes [colname]
  (let [id-keyword (keyword colname "id")]
    [(str "/" colname)
     ["/id/:id"
      {:get    {:summary    (str "Produce JSON representation of " (ia colname) " " colname " annotation")
                :parameters {:path {:id uuid?}}
                :handler    (cc/get-handler (keyword colname "id"))}
       :delete {:summary    (str "Delete " (ia colname) " " colname " annotation")
                :parameters {:path {:id uuid?}}
                :handler    (delete-assoc id-keyword)}
       :put    {:summary    (str "Update " (ia colname) " " colname " annotation. Pass a JSON as body with a \"value\" key.")
                :parameters {:path {:id uuid?}
                             :body {:value string?}}
                :handler    (put id-keyword)}}]]))

;; HEAD, DEPREL, and DEPS need special handling
(defn dep-routes []
  [["/head"
    ["/id/:id"
     {:get {:summary    (str "Produce JSON representation of a head annotation")
            :parameters {:path {:id uuid?}}
            :handler    (cc/get-handler :head/id)}
      :put {:summary     (str "Update a head annotation. Pass a JSON as body with a \"value\" key.")
            :description (str "The value should be one of three things: \n"
                              " \"root\" - special value for the head token of a sentence\n"
                              " null - if you wish to reset the value of the head\n"
                              " <uuid> - a uuid pointing to a valid *TOKEN*. Make sure this is not anything else.")
            :parameters  {:path {:id uuid?}
                          :body {:value any?}}
            :handler     put-head}}]]
   ["/deprel"
    ["/id/:id"
     {:get {:summary    (str "Produce JSON representation of a deprel annotation")
            :parameters {:path {:id uuid?}}
            :handler    (cc/get-handler :deprel/id)}
      :put {:summary    (str "Update a deprel annotation. Pass a JSON as body with a \"value\" key.")
            :parameters {:path {:id uuid?}
                         :body {:value string?}}
            :handler    put-deprel}}]]])

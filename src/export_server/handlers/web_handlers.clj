(ns export-server.handlers.web-handlers
  (:require [export-server.data.state :as state]
            [export-server.data.config :as config]

            [export-server.web.responce :refer :all]
            [export-server.web.logging :as log]
            [export-server.web.params-validator :as params-validator]

            [export-server.utils.rasterizator :as rastr]
            [export-server.browser.core :as browser]
            [export-server.sharing.twitter :as twitter]

            [me.raynes.fs :as fs]
            [cheshire.core :refer :all]
            [compojure.core :refer :all]
            [dk.ative.docjure.spreadsheet :as spreadheet]
            [clojure.data.csv :as csv-parser]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import (org.apache.commons.io.output ByteArrayOutputStream)))


;=======================================================================================================================
; Params to png/jpg/pdf
;=======================================================================================================================
(defn get-number-unit [m key] (Integer/parseInt (first (re-find #"([-+]?[0-9]+)" (m key)))))


(def allow-script-executing (atom true))


(defn get-boolean-unit [map key]
  (let [val (string/lower-case (map key))]
    (or (= val "true") (= val "1"))))


(defn get-pdf-x [params]
  (cond
    (contains? params "pdf-x") (get-number-unit params "pdf-x")
    (contains? params "x") (get-number-unit params "x")
    :else (:pdf-x config/defaults)))


(defn get-pdf-y [params]
  (cond
    (contains? params "pdf-x") (get-number-unit params "pdf-y")
    (contains? params "x") (get-number-unit params "y")
    :else (:pdf-y config/defaults)))


(defn get-force-transparent-white [params]
  (cond
    (contains? params "force-transparent-white") (params "force-transparent-white")
    (contains? params "forceTransparentWhite") (params "forceTransparentWhite")
    :else (:force-transparent-white config/defaults)))


(defn get-data-type [params]
  (cond
    (contains? params "data-type") (params "data-type")
    (contains? params "dataType") (params "dataType")
    :else nil))


(defn get-response-type [params]
  (cond
    (contains? params "response-type") (params "response-type")
    (contains? params "responseType") (params "responseType")
    :else nil))


(defn params-to-options [params]
  (let [pdf-width (when (contains? params "pdf-width") (get-number-unit params "pdf-width"))
        pdf-height (when (contains? params "pdf-height") (get-number-unit params "pdf-height"))

        {pdf-size-width :width pdf-size-height :height} (get config/available-pdf-sizes
                                                             (keyword (or (params "pdf-size") (params "pdfSize"))))
        pdf-size-width (when pdf-size-width (config/mm-to-pixel pdf-size-width))
        pdf-size-height (when pdf-size-height (config/mm-to-pixel pdf-size-height))

        pdf-width (or pdf-width pdf-size-width (:pdf-width config/defaults))
        pdf-height (or pdf-height pdf-size-height (:pdf-height config/defaults))

        pdf-landscape (if (contains? params "landscape") (get-boolean-unit params "landscape") (:pdf-landscape config/defaults))
        [pdf-width pdf-height] (if pdf-landscape [(max pdf-height pdf-width)
                                                  (min pdf-height pdf-width)]
                                                 [pdf-width pdf-height])]
    ;(prn "params-to-options"  pdf-width pdf-height)
    {:container-id            (get params "container-id" (:container-id config/defaults))
     :container-width         (or (get params "container-width")
                                  pdf-width
                                  (:container-width config/defaults))
     :container-height        (or (get params "container-height")
                                  pdf-height
                                  (:container-height config/defaults))
     :image-width             (or (when (contains? params "width") (get-number-unit params "width"))
                                  pdf-width
                                  (:image-width config/defaults))
     :image-height            (or (when (contains? params "height") (get-number-unit params "height"))
                                  pdf-height
                                  (:image-height config/defaults))
     :force-transparent-white (get-force-transparent-white params)
     :jpg-quality             (if (contains? params "quality") (read-string (params "quality")) (:jpg-quality config/defaults))
     :pdf-landscape           pdf-landscape
     :pdf-width               pdf-width
     :pdf-height              pdf-height
     :pdf-size                (or (params "pdf-size") (params "pdfSize") (:pdf-size config/defaults))
     :pdf-x                   (get-pdf-x params)
     :pdf-y                   (get-pdf-y params)}))


(defn to-png [params]
  (let [data (params "data")
        data-type (get-data-type params)
        options (params-to-options params)]
    (cond
      (and (= data-type "script") (not @allow-script-executing)) {:ok     false
                                                                  :result {:message   "Script executing is not allowed"
                                                                           :http-code 403}}
      (= data-type "svg") (browser/svg-to-png data false options)
      (= data-type "script") (browser/script-to-png data false options :png)
      :else {:ok false :result "Unknown data type"})))


(defn to-jpg [params]
  (let [data (params "data")
        data-type (get-data-type params)
        options (params-to-options params)]
    (cond
      (and (= data-type "script") (not @allow-script-executing)) {:ok     false
                                                                  :result {:message   "Script executing is not allowed"
                                                                           :http-code 403}}
      (= data-type "svg") (let [png-result (browser/svg-to-png data false options)]
                            (if (png-result :ok)
                              (rastr/png-to-jpg (png-result :result))
                              png-result))
      (= data-type "script") (let [png-result (browser/script-to-png data false options :png)]
                               (if (png-result :ok)
                                 (rastr/png-to-jpg (png-result :result))
                                 png-result))
      :else {:ok false :result "Unknown data type"})))


(defn to-pdf [params]
  (let [data (params "data")
        data-type (get-data-type params)
        options (params-to-options params)
        options (assoc options :image-width (:pdf-width options) :image-height (:pdf-height options))]
    (cond
      (and (= data-type "script") (not @allow-script-executing)) {:ok     false
                                                                  :result {:message   "Script executing is not allowed"
                                                                           :http-code 403}}
      ;(= data-type "svg") (rastr/svg-to-pdf data pdf-size landscape x y)
      (= data-type "svg") (let [png-result (browser/svg-to-png data false options)]
                            (if (:ok png-result)
                              (rastr/svg-to-pdf (:result png-result) options)
                              png-result))
      (= data-type "script") (let [png-result (browser/script-to-png data false options :png)]
                               (if (:ok png-result)
                                 (rastr/svg-to-pdf (:result png-result) options)
                                 png-result))
      :else {:ok false :result "Unknown data type"})))


(defn to-svg [params]
  (let [data (params "data")
        data-type (get-data-type params)
        options (params-to-options params)]
    (cond
      (and (= data-type "script") (not @allow-script-executing)) {:ok     false
                                                                  :result {:message   "Script executing is not allowed"
                                                                           :http-code 403}}
      (= data-type "svg") {:ok true :result data}
      (= data-type "script") (browser/script-to-png data false options :svg)
      :else {:ok false :result "Unknown data type"})))


(defn get-file-name [params] (if (and (contains? params "file-name") (string? (params "file-name"))) (params "file-name") "anychart"))


(defn save-file-and-get-url [data file-name extention]
  (if-let [folder (:saving-folder @state/options)]
    (let [new-file-name (rastr/get-file-name-hash file-name)
          path (str folder "/" new-file-name extention)]
      (fs/mkdirs folder)
      (io/copy data (io/file path))
      (json-success {:url (str (:saving-url-prefix @state/options) new-file-name extention)}))
    (json-error "Saving folder isn't specified.")))


;=======================================================================================================================
; Handlers
;=======================================================================================================================
(defn sharing-twitter [request]
  (let [params (request :form-params)
        validation-result (params-validator/validate-sharing-params params)]
    (if (params-validator/valid-result? validation-result)
      (let [{ok :ok result :result} (to-png params)]
        (if ok
          (twitter/twitter request (rastr/to-base64 result))
          (log/wrap-log-error json-error result request :processing)))
      (log/wrap-log-error json-error (params-validator/get-error-message validation-result) request :bad_params))))


(defn png [request]
  (let [params (request :form-params)
        validation-result (params-validator/validate-image-params params)]
    (if (params-validator/valid-result? validation-result)
      (let [{ok :ok result :result} (to-png params)
            response-type (get-response-type params)]
        (if ok
          (if (= response-type "base64")
            (if (params "save")
              (save-file-and-get-url (rastr/to-base64 result) (get-file-name params) ".base64")
              (json-success (rastr/to-base64 result)))
            (if (params "save")
              (save-file-and-get-url result (get-file-name params) ".png")
              (file-success result (get-file-name params) ".png")))
          (log/wrap-log-error json-error result request :processing)))
      (log/wrap-log-error json-error (params-validator/get-error-message validation-result) request :bad_params))))


(defn jpg [request]
  (let [params (request :form-params)
        validation-result (params-validator/validate-image-params params)]
    (if (params-validator/valid-result? validation-result)
      (let [{ok :ok result :result} (to-jpg params)
            response-type (get-response-type params)]
        (if ok
          (if (= response-type "base64")
            (if (params "save")
              (save-file-and-get-url (rastr/to-base64 result) (get-file-name params) ".base64")
              (json-success (rastr/to-base64 result)))
            (if (params "save")
              (save-file-and-get-url result (get-file-name params) ".jpg")
              (file-success result (get-file-name params) ".jpg")))
          (log/wrap-log-error json-error result request :processing)))
      (log/wrap-log-error json-error (params-validator/get-error-message validation-result) request :bad_params))))


(defn pdf [request]
  (let [params (request :form-params)
        validation-result (params-validator/validate-pdf-params params)]
    (if (params-validator/valid-result? validation-result)
      (let [{ok :ok result :result} (to-pdf params)
            response-type (get-response-type params)]
        (if ok
          (if (= response-type "base64")
            (if (params "save")
              (save-file-and-get-url (rastr/to-base64 result) (get-file-name params) ".base64")
              (json-success (rastr/to-base64 result)))
            (if (params "save")
              (save-file-and-get-url result (get-file-name params) ".pdf")
              (file-success result (get-file-name params) ".pdf")))
          (log/wrap-log-error json-error result request :processing)))
      (log/wrap-log-error json-error (params-validator/get-error-message validation-result) request :bad_params))))


(defn svg [request]
  (let [params (request :form-params)
        validation-result (params-validator/validate-image-params params)]
    (if (params-validator/valid-result? validation-result)
      (let [{ok :ok result :result} (to-svg params)
            response-type (get-response-type params)]
        (if ok
          (if (= response-type "base64")
            (if (params "save")
              (save-file-and-get-url (rastr/to-base64 (.getBytes result)) (get-file-name params) ".base64")
              (json-success (rastr/to-base64 (.getBytes result))))
            (if (params "save")
              (save-file-and-get-url (.getBytes result) (get-file-name params) ".svg")
              (file-success (.getBytes result) (get-file-name params) ".svg")))
          (log/wrap-log-error json-error result request :processing)))
      (log/wrap-log-error json-error (params-validator/get-error-message validation-result) request :bad_params))))


(defn xml [request]
  (let [params (request :form-params)
        validation-result (params-validator/validate-save-data-params params)]
    (if (params-validator/valid-result? validation-result)
      (file-success (.getBytes (params "data")) (get-file-name params) ".xml")
      (log/wrap-log-error json-error (params-validator/get-error-message validation-result) request :bad_params))))


(defn json [request]
  (let [params (request :form-params)
        validation-result (params-validator/validate-save-data-params params)]
    (if (params-validator/valid-result? validation-result)
      (file-success (.getBytes (params "data")) (get-file-name params) ".json")
      (log/wrap-log-error json-error (params-validator/get-error-message validation-result) request :bad_params))))


(defn csv [request]
  (let [params (request :form-params)
        validation-result (params-validator/validate-save-data-params params)]
    (if (params-validator/valid-result? validation-result)
      (file-success (.getBytes (params "data")) (get-file-name params) ".csv")
      (log/wrap-log-error json-error (params-validator/get-error-message validation-result) request :bad_params))))


(defn xlsx [request]
  (let [params (request :form-params)
        file-name (get-file-name params)
        validation-result (params-validator/validate-save-data-params params)]
    (if (params-validator/valid-result? validation-result)
      (let [csv (csv-parser/read-csv (params "data"))
            wb (spreadheet/create-workbook file-name csv)
            output (new ByteArrayOutputStream)]
        (spreadheet/save-workbook! output wb)
        (file-success (.toByteArray output) file-name ".xlsx"))
      (log/wrap-log-error json-error (params-validator/get-error-message validation-result) request :bad_params))))


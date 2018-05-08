(ns export-server.browser.etaoin.script-to-png
  (:require [export-server.browser.etaoin.common :as common]
            [export-server.browser.image-resizer :as image-resizer]
            [export-server.data.state :as state]
            [etaoin.api :refer :all]
            [etaoin.keys :as k]
            [clojure.java.io :as io]
            [export-server.data.config :as config]
            [taoensso.timbre :as timbre]
            [export-server.browser.templates :as html-templates])
  (:import (java.io File)))


;=======================================================================================================================
; Script --> SVG | PNG
;=======================================================================================================================
(def anychart-binary (slurp (io/resource "js/anychart-bundle.min.js")))
(def replacesvgsize (slurp (io/resource "js/replacesvgsize.min.js")))


;(defn get-svg [d]
;  (or (try
;        (let [inner (.getAttribute (.findElement d (By/cssSelector "#container div")) "innerHTML")
;              svg-end (.lastIndexOf inner "</svg>")
;              svg (subs inner 0 (+ 6 svg-end))]
;          svg)
;        (catch Exception e nil))
;      (.executeScript d "return document.getElementsByTagName(\"svg\")[0].outerHTML;" (into-array []))))


(defn exec-script-to-png-old-version [d script exit-on-error options type]
  (let [prev-handles (get-window-handles d)
        prev-handle (first prev-handles)]
    (js-execute d "window.open(\"\")")
    (let [new-handles (get-window-handles d)
          new-handle (first (clojure.set/difference (set new-handles) (set prev-handles)))]

      (switch-window d new-handle)
      (set-window-size d (:image-width options) (+ (:image-height options)
                                                   (if (= :firefox (:engine @state/options)) 75 0)))

      (let [startup
            (try
              (js-execute d "document.getElementsByTagName(\"body\")[0].style.margin = 0;
                             document.body.innerHTML = '<div id=\"' + arguments[0] + '\" style=\"width:' + arguments[1] + ';height:' + arguments[2] + ';\"></div>'"
                          (:container-id options)
                          (str (config/min-size (:image-width options) (:container-width options)))
                          (str (config/min-size (:image-height options) (:container-height options))))
              (catch Exception e (str "Failed to execute Startup Script\n" (.getMessage e))))

            binary
            (try
              (timbre/info "Exec binary!")
              (js-execute d anychart-binary)
              (catch Exception e
                ;(let [msg e]
                ;  (spit (io/file "/media/ssd/sibental/export-server/e.txt") msg))
                (str "Failed to execute AnyChart Binary File\n" e (.getMessage e))))

            script
            (try
              (js-execute d script)
              (catch Exception e (str "Failed to execute Script\n" e (.getMessage e))))

            waiting (Thread/sleep 100)
            ;(try
            ;  (let [now (System/currentTimeMillis)]
            ;    (loop []
            ;      (if (not-empty (js-execute d "return document.getElementsByTagName(\"svg\");"))
            ;        nil
            ;        (if (> (System/currentTimeMillis) (+ now 3000))
            ;          nil
            ;          (do
            ;            (Thread/sleep 10)
            ;            (recur))))))
            ;  (catch Exception e (str "Failed to wait for SVG\n" (.getMessage e))))

            svg
            (try
              (js-execute d "return document.getElementsByTagName(\"svg\")[0].outerHTML;")
              (catch Exception e (str "Failed to take SVG Structure\n" (.getMessage e))))

            screenshot (screenshot d nil)
            screenshot (if (= :firefox (:engine @state/options))
                         (image-resizer/resize-image screenshot options)
                         screenshot)

            error (some #(when (not (nil? %)) %) [startup binary script waiting])]
        (println :errs [startup binary script waiting])

        (js-execute d "window.close(\"\")")
        (switch-window d prev-handle)

        (if error
          (if exit-on-error
            (common/exit d 1 error)
            {:ok false :result error})
          (case type
            :png {:ok true :result screenshot}
            :svg {:ok true :result svg}))))))



(defn exec-script-to-png [d script exit-on-error options type]
  (let [prev-handles (get-window-handles d)
        prev-handle (first prev-handles)]
    (js-execute d "window.open(\"\")")
    (let [new-handles (get-window-handles d)
          new-handle (first (clojure.set/difference (set new-handles) (set prev-handles)))]

      (try (switch-window d new-handle)
           (catch Exception e (prn :ex e)))
      (set-window-size d (:image-width options) (+ (:image-height options)
                                                   (if (= :firefox (:engine @state/options)) 75 0)))

      (let [startup (try
                      (let [html (html-templates/create-script-html options script)
                            tmp-file (File/createTempFile "anychart-export-server" "")]
                        (spit tmp-file html)
                        (go d (str "file://" (.getAbsolutePath tmp-file)))
                        (.delete tmp-file)
                        nil)
                      (catch Exception e (str "Failed to execute Startup Script\n" (.getMessage e))))

            waiting nil
            ;(try
            ;  (let [now (System/currentTimeMillis)]
            ;    (loop []
            ;      (if (not-empty (js-execute d "return document.getElementsByTagName(\"svg\");"))
            ;        nil
            ;        (if (> (System/currentTimeMillis) (+ now 3000))
            ;          nil
            ;          (do
            ;            (Thread/sleep 10)
            ;            (recur))))))
            ;  (catch Exception e (str "Failed to wait for SVG\n" (.getMessage e))))

            svg
            (try
              (js-execute d "return document.getElementsByTagName(\"svg\")[0].outerHTML;")
              (catch Exception e (str "Failed to take SVG Structure\n" (.getMessage e))))

            screenshot (screenshot d nil)
            screenshot (if (= :firefox (:engine @state/options))
                         (image-resizer/resize-image screenshot options)
                         screenshot)

            error (some #(when (not (nil? %)) %) [startup waiting])]
        ;(println :errs [startup waiting])

        (js-execute d "window.close(\"\")")
        (switch-window d prev-handle)

        (if error
          (if exit-on-error
            (common/exit d 1 error)
            {:ok false :result error})
          (case type
            :png {:ok true :result screenshot}
            :svg {:ok true :result svg}))))))



(defn script-to-png [script quit-ph exit-on-error options type]
  (if-let [driver (if quit-ph (common/create-driverr) (common/get-free-driver))]
    (let [svg (exec-script-to-png driver script exit-on-error options type)]
      (if quit-ph (quit driver) (common/return-driver driver))
      svg)
    {:ok false :result "Driver isn't available\n"}))
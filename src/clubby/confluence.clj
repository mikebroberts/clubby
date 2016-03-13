(ns clubby.confluence
  (:require [clubby.core :refer :all])
  (:gen-class))

(defn story->name-and-link [story]
  (str "[" (clojure.string/escape (:name story) {\[ "(" \] ")" \| "&#124;"}) "|" (web-url-for-story (:project_id story) (:id story)) "]"))

(defn finished-story->confluence [{:keys [workflow_state cycle-time-hours labels] :as story}]
  (str "|" (story->name-and-link story)
       "|" workflow_state
       "|" cycle-time-hours
       "|" (apply str (interpose " " (map :name labels))) " " ; Adding space in case no labels
       "|"))

(defn kanban-story->confluence [story]
  (if story
    (story->name-and-link story)
    " "))

(defn kanban-story-row->confluence [stories]
  (str "|" (apply str (interpose "|" (map kanban-story->confluence stories))) "|"))

(defn kanban->confluence-kanban [story-grid]
  (str
    (str "||" (apply str (interpose "||" (map :workflow_state (first story-grid)))) "||\n")
    (apply str (interpose "\n" (map kanban-story-row->confluence story-grid))) "\n"
    ))

(defn ipm-report [kanban-stories recently-completed-stories]
  (str
    "h2. Completed Stories\n"
    "||Name||State||Time to complete (hours)||Labels||\n"
    (apply str (interpose "\n" (map finished-story->confluence recently-completed-stories))) "\n"
    "*Mean Completion Time*: " (int (calc-mean-completion-time recently-completed-stories)) " hours\n"
    "h2. Stories In Play\n"
    (kanban->confluence-kanban kanban-stories)
    ))


-- ==============================================================================
-- StudyVault Phase 6 Migration: Topic preparation tracking, revision history and
-- exam planning. Safe to re-run (idempotent).
-- ==============================================================================

-- 1. Topic preparation tracking columns -----------------------------------------
-- completed_at records exactly when a topic first reached COMPLETED and is cleared
-- when the topic leaves that state. revision_count mirrors the number of recorded
-- revision_history rows so the UI can show it without an extra aggregate query.
ALTER TABLE public.topics
    ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ NULL;

ALTER TABLE public.topics
    ADD COLUMN IF NOT EXISTS revision_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE public.topics
    ADD COLUMN IF NOT EXISTS last_studied_at TIMESTAMPTZ NULL;

-- 2. Topic-level revision history ----------------------------------------------
-- Each row is a single "Mark as Revised" action. revision_number is a per-topic
-- running counter; previous_status preserves the status before the revision so the
-- timeline can show the transition.
CREATE TABLE IF NOT EXISTS public.revision_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES public.topics(id) ON DELETE CASCADE,
    studied_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    revision_number INTEGER NOT NULL DEFAULT 1,
    previous_status TEXT NULL,
    new_status TEXT NOT NULL,
    notes TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_revision_history_user_topic
    ON public.revision_history(user_id, topic_id);
CREATE INDEX IF NOT EXISTS idx_revision_history_topic_id
    ON public.revision_history(topic_id);
CREATE INDEX IF NOT EXISTS idx_revision_history_studied_at
    ON public.revision_history(studied_at DESC);

ALTER TABLE public.revision_history ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view own revision history" ON public.revision_history;
CREATE POLICY "Users can view own revision history"
ON public.revision_history FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert own revision history" ON public.revision_history;
CREATE POLICY "Users can insert own revision history"
ON public.revision_history FOR INSERT
TO authenticated
WITH CHECK (
    auth.uid() = user_id
    AND EXISTS (
        SELECT 1 FROM public.topics t
        JOIN public.units u ON t.unit_id = u.id
        JOIN public.subjects s ON u.subject_id = s.id
        WHERE t.id = revision_history.topic_id
        AND s.user_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Users can update own revision history" ON public.revision_history;
CREATE POLICY "Users can update own revision history"
ON public.revision_history FOR UPDATE
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (
    auth.uid() = user_id
    AND EXISTS (
        SELECT 1 FROM public.topics t
        JOIN public.units u ON t.unit_id = u.id
        JOIN public.subjects s ON u.subject_id = s.id
        WHERE t.id = revision_history.topic_id
        AND s.user_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Users can delete own revision history" ON public.revision_history;
CREATE POLICY "Users can delete own revision history"
ON public.revision_history FOR DELETE
TO authenticated
USING (auth.uid() = user_id);

-- 3. Exam planning -------------------------------------------------------------
-- exam_date is stored as a DATE so day counts are timezone independent; name is the
-- exam title (e.g. "Mid Term"), description holds optional notes.
CREATE TABLE IF NOT EXISTS public.exams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES public.subjects(id) ON DELETE CASCADE,
    name TEXT NOT NULL DEFAULT 'Exam',
    exam_date DATE NOT NULL,
    venue TEXT NULL,
    description TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_exams_user_subject
    ON public.exams(user_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_exams_subject_id
    ON public.exams(subject_id);
CREATE INDEX IF NOT EXISTS idx_exams_exam_date
    ON public.exams(exam_date);

ALTER TABLE public.exams ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can view own exams" ON public.exams;
CREATE POLICY "Users can view own exams"
ON public.exams FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "Users can insert own exams" ON public.exams;
CREATE POLICY "Users can insert own exams"
ON public.exams FOR INSERT
TO authenticated
WITH CHECK (
    auth.uid() = user_id
    AND EXISTS (
        SELECT 1 FROM public.subjects
        WHERE subjects.id = exams.subject_id
        AND subjects.user_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Users can update own exams" ON public.exams;
CREATE POLICY "Users can update own exams"
ON public.exams FOR UPDATE
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (
    auth.uid() = user_id
    AND EXISTS (
        SELECT 1 FROM public.subjects
        WHERE subjects.id = exams.subject_id
        AND subjects.user_id = auth.uid()
    )
);

DROP POLICY IF EXISTS "Users can delete own exams" ON public.exams;
CREATE POLICY "Users can delete own exams"
ON public.exams FOR DELETE
TO authenticated
USING (auth.uid() = user_id);

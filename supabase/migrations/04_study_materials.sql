-- ==============================================================================
-- StudyVault Phase 4 Migration: Secure Supabase Storage & study_materials table
-- ==============================================================================

-- 1. Create the private Storage bucket for study materials
-- The bucket is private (public = false) and enforces a 25 MB safe practical file size limit to prevent memory buffer issues.
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'study-materials',
    'study-materials',
    false,
    26214400, -- 25 MB in bytes
    NULL      -- All standard study document MIME types accepted
)
ON CONFLICT (id) DO UPDATE SET
    public = false,
    file_size_limit = 26214400;

-- 2. Storage RLS Policies for 'study-materials' bucket
-- Storage objects path structure: {userId}/{subjectId}/{uniqueFileId}_{safeFileName}
-- The first folder segment must strictly match auth.uid()::text to guarantee user isolation.
-- Each policy is dropped first so this migration can be re-run safely (idempotent).

DROP POLICY IF EXISTS "Users can upload their own study materials to storage" ON storage.objects;
CREATE POLICY "Users can upload their own study materials to storage"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (
    bucket_id = 'study-materials'
    AND (storage.foldername(name))[1] = (auth.uid())::text
);

DROP POLICY IF EXISTS "Users can read their own study materials from storage" ON storage.objects;
CREATE POLICY "Users can read their own study materials from storage"
ON storage.objects FOR SELECT
TO authenticated
USING (
    bucket_id = 'study-materials'
    AND (storage.foldername(name))[1] = (auth.uid())::text
);

DROP POLICY IF EXISTS "Users can update their own study materials in storage" ON storage.objects;
CREATE POLICY "Users can update their own study materials in storage"
ON storage.objects FOR UPDATE
TO authenticated
USING (
    bucket_id = 'study-materials'
    AND (storage.foldername(name))[1] = (auth.uid())::text
)
WITH CHECK (
    bucket_id = 'study-materials'
    AND (storage.foldername(name))[1] = (auth.uid())::text
);

DROP POLICY IF EXISTS "Users can delete their own study materials from storage" ON storage.objects;
CREATE POLICY "Users can delete their own study materials from storage"
ON storage.objects FOR DELETE
TO authenticated
USING (
    bucket_id = 'study-materials'
    AND (storage.foldername(name))[1] = (auth.uid())::text
);

-- 3. Create the study_materials table in public schema
CREATE TABLE IF NOT EXISTS public.study_materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES public.subjects(id) ON DELETE CASCADE,
    unit_id UUID NULL REFERENCES public.units(id) ON DELETE SET NULL,
    topic_id UUID NULL REFERENCES public.topics(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    original_filename TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- 4. Indexes for fast retrieval and joins
CREATE INDEX IF NOT EXISTS idx_study_materials_user_subject ON public.study_materials(user_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_study_materials_subject_id ON public.study_materials(subject_id);
CREATE INDEX IF NOT EXISTS idx_study_materials_unit_id ON public.study_materials(unit_id);
CREATE INDEX IF NOT EXISTS idx_study_materials_topic_id ON public.study_materials(topic_id);
CREATE INDEX IF NOT EXISTS idx_study_materials_created_at ON public.study_materials(created_at DESC);

-- 5. Enable Row Level Security (RLS)
ALTER TABLE public.study_materials ENABLE ROW LEVEL SECURITY;

-- 6. Row Level Security Policies
-- Policies are dropped first so this migration is idempotent and can be safely re-run.
-- NOTE: These policies assume public.subjects and public.units both expose a `user_id`
-- column (as established by earlier migrations). If a real schema does not have those
-- columns, the ownership predicates must be adjusted to the actual schema before applying.
-- SELECT: Users can only view their own study materials
DROP POLICY IF EXISTS "Users can view own study materials" ON public.study_materials;
CREATE POLICY "Users can view own study materials"
ON public.study_materials FOR SELECT
TO authenticated
USING (auth.uid() = user_id);

-- INSERT: Enforce ownership, valid subject, unit belonging to subject, and topic belonging to unit/subject via units join
DROP POLICY IF EXISTS "Users can insert own study materials" ON public.study_materials;
CREATE POLICY "Users can insert own study materials"
ON public.study_materials FOR INSERT
TO authenticated
WITH CHECK (
    auth.uid() = user_id
    AND EXISTS (
        SELECT 1 FROM public.subjects
        WHERE subjects.id = study_materials.subject_id
        AND subjects.user_id = auth.uid()
    )
    AND (
        unit_id IS NULL OR EXISTS (
            SELECT 1 FROM public.units
            WHERE units.id = study_materials.unit_id
            AND units.subject_id = study_materials.subject_id
            AND units.user_id = auth.uid()
        )
    )
    AND (
        topic_id IS NULL OR EXISTS (
            SELECT 1 FROM public.topics t
            JOIN public.units u ON t.unit_id = u.id
            WHERE t.id = study_materials.topic_id
            AND u.subject_id = study_materials.subject_id
            AND (study_materials.unit_id IS NULL OR t.unit_id = study_materials.unit_id)
            AND u.user_id = auth.uid()
        )
    )
);

-- UPDATE: Enforce ownership and strict relational hierarchy checks via units join
DROP POLICY IF EXISTS "Users can update own study materials" ON public.study_materials;
CREATE POLICY "Users can update own study materials"
ON public.study_materials FOR UPDATE
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (
    auth.uid() = user_id
    AND EXISTS (
        SELECT 1 FROM public.subjects
        WHERE subjects.id = study_materials.subject_id
        AND subjects.user_id = auth.uid()
    )
    AND (
        unit_id IS NULL OR EXISTS (
            SELECT 1 FROM public.units
            WHERE units.id = study_materials.unit_id
            AND units.subject_id = study_materials.subject_id
            AND units.user_id = auth.uid()
        )
    )
    AND (
        topic_id IS NULL OR EXISTS (
            SELECT 1 FROM public.topics t
            JOIN public.units u ON t.unit_id = u.id
            WHERE t.id = study_materials.topic_id
            AND u.subject_id = study_materials.subject_id
            AND (study_materials.unit_id IS NULL OR t.unit_id = study_materials.unit_id)
            AND u.user_id = auth.uid()
        )
    )
);

-- DELETE: Users can delete their own study materials
DROP POLICY IF EXISTS "Users can delete own study materials" ON public.study_materials;
CREATE POLICY "Users can delete own study materials"
ON public.study_materials FOR DELETE
TO authenticated
USING (auth.uid() = user_id);

module Luna.Syntax.Name.Path where

import Prologue

import Data.List.Split      (splitWhen)
import Luna.Syntax.Name.Class


-- === Definitions === --

newtype QualPath = QualPath [Name] deriving (Show, Eq, Ord)
makeWrapped ''QualPath


-- === Utils === --

mk :: ToString s => s -> QualPath
mk = QualPath ∘ fmap fromString ∘ splitWhen (== '.') ∘ toString


-- === Instances === --

instance IsString QualPath where fromString = mk
instance ToString QualPath where toString   = intercalate "." ∘ fmap toString ∘ unwrap'

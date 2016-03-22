{-# LANGUAGE CPP                    #-}
{-# LANGUAGE UndecidableInstances   #-}
{-# LANGUAGE FunctionalDependencies #-}

{-# LANGUAGE NoMonomorphismRestriction #-}

module Luna.Syntax.Term.Expr (module Luna.Syntax.Term.Expr, module X) where

import           Prologue.Unsafe (undefined)
import           Prelude.Luna                 hiding (Num, Swapped)
import qualified Prelude.Luna                 as P

import           Data.Abstract
import           Data.Base
import           Data.Record                  hiding (Layout, Variants, Match, Cons)
import qualified Data.Record                  as Record
import           Type.Cache.TH                (assertTypesEq, cacheHelper, cacheType)
import           Type.Container
import           Type.Map

import           Data.Typeable                (splitTyConApp, tyConName, typeRepTyCon)
import           Luna.Runtime.Dynamics      (Dynamics, Dynamic, Static, ToDynamic, ToStatic, SubRuntimes, SubSemiRuntimes, ByRuntime)
import qualified Luna.Runtime.Dynamics      as Dynamics
import           Luna.Pretty.Styles
import           Luna.Syntax.Term.Function.Argument
import qualified Data.Reprx                   as Repr
import           Type.Bool
import           Luna.Syntax.Term.Format        as Eval
import qualified Luna.Syntax.Term.Lit     as Lit
import Luna.Syntax.Term.Atom as X

import Data.Record.Model.Masked as X (Data, TermRecord)





-----------------------------
-- === Component types === --
-----------------------------

data Var'    = Var'    deriving (Show, Eq, Ord)
data Cons'   = Cons'   deriving (Show, Eq, Ord)
data Acc'    = Acc'    deriving (Show, Eq, Ord)
data App'    = App'    deriving (Show, Eq, Ord)
data Unify'  = Unify'  deriving (Show, Eq, Ord)
data Match'  = Match'  deriving (Show, Eq, Ord)
data Lam'    = Lam'    deriving (Show, Eq, Ord)
data Native' = Native' deriving (Show, Eq, Ord)
data Blank'  = Blank'  deriving (Show, Eq, Ord)



data family Expr atom runtime a

newtype instance Expr Var'    rt a = T_Var     (NameByRuntime rt a)
newtype instance Expr Cons'   rt a = T_Cons    (NameByRuntime rt a)
data    instance Expr Acc'    rt a = T_Acc    !(NameByRuntime rt a) !a
data    instance Expr App'    rt a = T_App                          !a ![Arg a]
data    instance Expr Unify'  rt a = T_Unify                        !a !a
data    instance Expr Match'  rt a = T_Match                        !a !a
data    instance Expr Lam'    rt a = T_Lam                          ![Arg a] !a
data    instance Expr Native' rt a = T_Native !(NameByRuntime rt a)
data    instance Expr Blank'  rt a = T_Blank


--var   = Record.cons ∘  Var
--unify = Record.cons ∘∘ Unify
--match = Record.cons ∘∘ Match





-----------------------------
-- === Term definition === --
-----------------------------

-- | The following definitions are parameterized by the `t` type, which indicates which data `Layout` to choose.
--   The `Layout` type family defines the recursive layout for AST structures.

newtype     Term     t term rt = Term (TermRecord (SubRuntimeGroups rt t term) (Variants t term rt) t) deriving (Generic, NFData, Show)
type        Variants t term rt = Elems term (NameByRuntime rt (Layout t term rt)) (Layout t term rt)
type family Layout   t term rt
--type family LayoutOf   a
type family LayoutType a
type family TermOf     a

type family Input     a
type family NameInput a where
    NameInput I = Impossible
    NameInput a = If (Dynamics a == Static) Lit.String (Input a)


-- === Elems === --

type family   Elems term  n t :: [*]

type instance Elems Lit    n t = Lit.Star
                              ': Lit.String
                              ': Lit.Number
                              ': '[]

type instance Elems Val    n t = Cons         n t
                              ': Lam            t
                              ': Elems Lit    n t

type instance Elems Thunk  n t = Acc          n t
                              ': App            t
                              ': Native       n
                              ': Elems Val    n t

type instance Elems Phrase n t = Var          n
                              ': Unify          t
                              ': Match          t
                              ': Elems Thunk  n t

type instance Elems Draft  n t = Blank
                              ': Elems Phrase n t



-- === Syntax Layouts === --

type family SubSemiTerms ts term where
    SubSemiTerms '[]       term = '[]
    SubSemiTerms (t ': ts) t    = '[t]
    SubSemiTerms (t ': ts) term = t ': SubSemiTerms ts term

type ApplySubRuntimes     rt t a = ApplyLayouts (SubRuntimes     rt) t a
type ApplySubSemiRuntimes rt t a = ApplyLayouts (SubSemiRuntimes rt) t a
type family ApplyLayouts rts t a where ApplyLayouts '[]         t a = '[]
                                       ApplyLayouts (rt ': rts) t a = Term a t rt ': ApplyLayouts rts t a

type SubRuntimeGroups rt t a = SubRuntimeGroups' rt t (SubSemiTerms Eval.Models a)
type family SubRuntimeGroups' rt t gs where
  SubRuntimeGroups' rt t '[]       = '[]
  SubRuntimeGroups' rt t '[g]      = ApplySubRuntimes     rt t g
  SubRuntimeGroups' rt t (g ': gs) = ApplySubSemiRuntimes rt t g <> SubRuntimeGroups' rt t gs

type NameByRuntime rt d = ByRuntime rt Lit.String d


-- === Variant repr === --

type VariantRepr s rec = WithElement' ElemShow rec (Repr.Builder s Repr.Tok)

class                                                 ElemShow a out where elemShow :: a -> out
instance (Repr s a, Repr.Builder s Repr.Tok ~ out) => ElemShow a out where elemShow = repr

instance {-# OVERLAPPABLE #-}  VariantRepr s (TermRecord gs vs t)                         => Repr s          (TermRecord gs vs t)  where repr   = variantRepr                                      ; {-# INLINE repr #-}
instance {-# OVERLAPPABLE #-} (VariantRepr s (Unwrapped (Term t term rt)), Typeable term) => Repr s          (Term      t term rt) where repr t = fromString (showTermType t) <+> repr (unwrap' t) ; {-# INLINE repr #-}
instance                       VariantRepr HeaderOnly (Unwrapped (Term t term rt))        => Repr HeaderOnly (Term      t term rt) where repr   = repr ∘ unwrap'                                   ; {-# INLINE repr #-}

variantRepr :: VariantRepr s rec => rec -> Repr.Builder s Repr.Tok
variantRepr = withElement' (Proxy :: Proxy ElemShow) elemShow


-- === Utils === --

showTermType :: Typeable term => Term t term rt -> String
showTermType (t :: Term t term rt) = tyConName $ typeRepTyCon $ head $ snd $ splitTyConApp $ typeOf (Proxy :: Proxy term)


-- === Instances === --

-- Basic instances
type instance Dynamics   (Term t term rt) = rt
type instance LayoutType (Term t term rt) = t
type instance TermOf     (Term t term rt) = Term t term rt

instance Eq  (Term t term rt) where (==)    = $notImplemented
instance Ord (Term t term rt) where compare = $notImplemented

-- Bases
type instance Base (Term t term rt) = Proxy term

-- Wrappers & Layers
makeWrapped ''Term
type instance Unlayered (Term t term rt) = Unwrapped (Term t term rt)
instance      Layered   (Term t term rt)
instance      Rewrapped (Term t term rt) (Term t' term' rt')

-- Record instances
type instance RecordOf (Term t term rt) = RecordOf (Unlayered (Term t term rt))
instance IsRecord (Unlayered (Term t term rt)) => IsRecord (Term t term rt) where asRecord = wrapped' ∘ asRecord

-- Layouts
type instance ToStatic  (Term t term rt) = Term t term (ToStatic  rt)
type instance ToDynamic (Term t term rt) = Term t term (ToDynamic rt)

-- Properties
type instance Props p (Term t term rt) = Props p (RecordOf (Term t term rt))

-- Conversions
instance Unwrapped (Term t term rt) ~ TermRecord gs vs t' => Convertible (Term t term rt) (TermRecord gs vs t') where convert = unwrap' ; {-# INLINE convert #-}

instance Convertible (Unwrapped (Term t term rt)) Data => Castable    (Term t term rt) Data
instance Convertible (Unwrapped (Term t term rt)) Data => Convertible (Term t term rt) Data where convert = convert ∘ unwrap' ; {-# INLINE convert #-}
instance Castable    Data (Unwrapped (Term t term rt)) => Castable    Data (Term t term rt) where cast    = wrap'   ∘ cast    ; {-# INLINE cast    #-}


-- Abstractions
type instance                                                       Abstract    (Term t term rt) = Data
instance BiCastable (Abstract (Term t term rt)) (Term t term rt) => IsAbstract  (Term t term rt) where abstracted = iso cast cast
instance BiCastable (Abstract (Term t term rt)) (Term t term rt) => HasAbstract (Term t term rt)



-------------------------------------
-- === Term Layout type caches === --
-------------------------------------

-- | Options in this section should be used only for development purpose and should never be enabled in production ready code.
-- | Their behaviour bases often on manually cached code, which could accidentaly get obsolete.
-- | We could probably throw it away in the future, but the following GHC bugs have to be resolved first:
-- |    - https://ghc.haskell.org/trac/ghc/ticket/8095
-- |    - https://ghc.haskell.org/trac/ghc/ticket/11375

#ifndef RELEASE
#ifdef  FastCompilation
#define CachedTypeFamilies
#endif
#endif

-- Cache related pragmas
#define CACHE(n)       cacheHelper ''n Nothing  ; cacheType ''n Nothing
#define CACHE_AS(n,cn) cacheHelper ''n (Just cn); cacheType ''n (Just cn)
#define CHECK_EQ(s,t)  assertTypesEq (Proxy :: Proxy (s)) (Proxy :: Proxy (t))


-- === Definitions === --

-- The following code is result of type-families expressions and is cached in order to speed-up the compilation process.
-- related GHC bug:        https://ghc.haskell.org/trac/ghc/ticket/8095#no1
-- related IRC discussion: http://pastebin.com/9PH7TPB9

-- | All possible groups and variants stored as single 64-bit mask:
-- |   - 9  bits for groups
-- |   - 36 bits for variants
-- |   - 19 bits free for further extensions

-- === VariantList === --

type  GroupList t =              '[ {-  0 -} Term t Lit   Static
                                  , {-  1 -} Term t Val   Static
                                  , {-  2 -} Term t Val   Dynamic
                                  , {-  3 -} Term t Thunk Static
                                  , {-  4 -} Term t Thunk Dynamic
                                  , {-  5 -} Term t Phrase Static
                                  , {-  6 -} Term t Phrase Dynamic
                                  , {-  7 -} Term t Draft Static
                                  , {-  8 -} Term t Draft Dynamic
                                  ]
type VariantList_MANUAL_CACHE t = [ {-  9 -} Lit.Star
                                  , {- 10 -} Lit.String
                                  , {- 11 -} Lit.Number
                                  , {- 12 -} Cons   Lit.String (Layout t Val   Static )
                                  , {- 13 -} Lam               (Layout t Val   Static )
                                  , {- 14 -} Cons              (Layout t Val   Dynamic) (Layout t Val Dynamic)
                                  , {- 15 -} Lam               (Layout t Val   Dynamic)
                                  , {- 16 -} Cons   Lit.String (Layout t Thunk Static )
                                  , {- 17 -} Acc    Lit.String (Layout t Thunk Static )
                                  , {- 18 -} App               (Layout t Thunk Static )
                                  , {- 19 -} Lam               (Layout t Thunk Static )
                                  , {- 20 -} Native Lit.String
                                  , {- 21 -} Acc               (Layout t Thunk Dynamic) (Layout t Thunk Dynamic)
                                  , {- 22 -} App               (Layout t Thunk Dynamic)
                                  , {- 23 -} Cons              (Layout t Thunk Dynamic) (Layout t Thunk Dynamic)
                                  , {- 24 -} Lam               (Layout t Thunk Dynamic)
                                  , {- 25 -} Native            (Layout t Thunk Dynamic)
                                  , {- 26 -} Var    Lit.String
                                  , {- 27 -} Cons   Lit.String (Layout t Phrase Static )
                                  , {- 28 -} Unify             (Layout t Phrase Static )
                                  , {- 29 -} Match             (Layout t Phrase Static )
                                  , {- 30 -} Acc    Lit.String (Layout t Phrase Static )
                                  , {- 31 -} App               (Layout t Phrase Static )
                                  , {- 32 -} Lam               (Layout t Phrase Static )
                                  , {- 33 -} Var               (Layout t Phrase Dynamic)
                                  , {- 34 -} Unify             (Layout t Phrase Dynamic)
                                  , {- 35 -} Match             (Layout t Phrase Dynamic)
                                  , {- 36 -} Acc               (Layout t Phrase Dynamic) (Layout t Phrase Dynamic)
                                  , {- 37 -} App               (Layout t Phrase Dynamic)
                                  , {- 38 -} Cons              (Layout t Phrase Dynamic) (Layout t Phrase Dynamic)
                                  , {- 39 -} Lam               (Layout t Phrase Dynamic)
                                  , {- 40 -} Native            (Layout t Phrase Dynamic)
                                  , {- 41 -} Blank
                                  , {- 42 -} Cons   Lit.String (Layout t Draft Static )
                                  , {- 43 -} Unify             (Layout t Draft Static )
                                  , {- 44 -} Match             (Layout t Draft Static )
                                  , {- 45 -} Acc    Lit.String (Layout t Draft Static )
                                  , {- 46 -} App               (Layout t Draft Static )
                                  , {- 47 -} Lam               (Layout t Draft Static )
                                  , {- 48 -} Var               (Layout t Draft Dynamic)
                                  , {- 49 -} Unify             (Layout t Draft Dynamic)
                                  , {- 50 -} Match             (Layout t Draft Dynamic)
                                  , {- 51 -} Acc               (Layout t Draft Dynamic) (Layout t Draft Dynamic)
                                  , {- 52 -} App               (Layout t Draft Dynamic)
                                  , {- 53 -} Cons              (Layout t Draft Dynamic) (Layout t Draft Dynamic)
                                  , {- 54 -} Lam               (Layout t Draft Dynamic)
                                  , {- 55 -} Native            (Layout t Draft Dynamic)
                                  ]

#ifndef CachedTypeFamilies

FIXME
--type VariantList_RULE t = Unique (GatherProps Variant (GroupList t))
--CACHE_AS(VariantList_RULE, VariantList_GEN_CACHE)
--CHECK_EQ(VariantList_GEN_CACHE IM, VariantList_MANUAL_CACHE IM)
--type VariantList_CACHE t = VariantList_GEN_CACHE t

#else

type VariantList_CACHE t = VariantList_MANUAL_CACHE t

#endif

type VariantList t = VariantList_CACHE t

-- Layout

type Layout_RULE t = GroupList t <> VariantList t
CACHE_AS(Layout_RULE, "Layout_CACHE")

type instance Record.Layout (TermRecord gs vs t) = Layout_CACHE t

type instance Layout_Variants Variant (TermRecord gs vs t) = VariantList t

-- === DecodeMap === --

type DecodeMap_MANUAL_CACHE t =
    'Map [ {-  0 -} '( Term t Lit   Static                                                 ,  0 )
         , {-  1 -} '( Term t Val   Static                                                 ,  1 )
         , {-  2 -} '( Term t Val   Dynamic                                                ,  2 )
         , {-  3 -} '( Term t Thunk Static                                                 ,  3 )
         , {-  4 -} '( Term t Thunk Dynamic                                                ,  4 )
         , {-  5 -} '( Term t Phrase Static                                                 ,  5 )
         , {-  6 -} '( Term t Phrase Dynamic                                                ,  6 )
         , {-  7 -} '( Term t Draft Static                                                 ,  7 )
         , {-  8 -} '( Term t Draft Dynamic                                                ,  8 )
         , {-  9 -} '( Lit.Star                                                            ,  9 )
         , {- 10 -} '( Lit.String                                                          , 10 )
         , {- 11 -} '( Lit.Number                                                          , 11 )
         , {- 12 -} '( Cons   Lit.String (Layout t Val   Static )                          , 12 )
         , {- 13 -} '( Lam               (Layout t Val   Static )                          , 13 )
         , {- 14 -} '( Cons              (Layout t Val   Dynamic) (Layout t Val   Dynamic) , 14 )
         , {- 15 -} '( Lam               (Layout t Val   Dynamic)                          , 15 )
         , {- 16 -} '( Cons   Lit.String (Layout t Thunk Static )                          , 16 )
         , {- 17 -} '( Acc    Lit.String (Layout t Thunk Static )                          , 17 )
         , {- 18 -} '( App               (Layout t Thunk Static )                          , 18 )
         , {- 19 -} '( Lam               (Layout t Thunk Static )                          , 19 )
         , {- 20 -} '( Native Lit.String                                                   , 20 )
         , {- 21 -} '( Acc               (Layout t Thunk Dynamic) (Layout t Thunk Dynamic) , 21 )
         , {- 22 -} '( App               (Layout t Thunk Dynamic)                          , 22 )
         , {- 23 -} '( Cons              (Layout t Thunk Dynamic) (Layout t Thunk Dynamic) , 23 )
         , {- 24 -} '( Lam               (Layout t Thunk Dynamic)                          , 24 )
         , {- 25 -} '( Native            (Layout t Thunk Dynamic)                          , 25 )
         , {- 26 -} '( Var    Lit.String                                                   , 26 )
         , {- 27 -} '( Cons   Lit.String (Layout t Phrase Static )                          , 27 )
         , {- 28 -} '( Unify             (Layout t Phrase Static )                          , 28 )
         , {- 29 -} '( Match             (Layout t Phrase Static )                          , 29 )
         , {- 30 -} '( Acc    Lit.String (Layout t Phrase Static )                          , 30 )
         , {- 31 -} '( App               (Layout t Phrase Static )                          , 31 )
         , {- 32 -} '( Lam               (Layout t Phrase Static )                          , 32 )
         , {- 33 -} '( Var               (Layout t Phrase Dynamic)                          , 33 )
         , {- 34 -} '( Unify             (Layout t Phrase Dynamic)                          , 34 )
         , {- 35 -} '( Match             (Layout t Phrase Dynamic)                          , 35 )
         , {- 36 -} '( Acc               (Layout t Phrase Dynamic) (Layout t Phrase Dynamic) , 36 )
         , {- 37 -} '( App               (Layout t Phrase Dynamic)                          , 37 )
         , {- 38 -} '( Cons              (Layout t Phrase Dynamic) (Layout t Phrase Dynamic) , 38 )
         , {- 39 -} '( Lam               (Layout t Phrase Dynamic)                          , 39 )
         , {- 40 -} '( Native            (Layout t Phrase Dynamic)                          , 40 )
         , {- 41 -} '( Blank                                                               , 41 )
         , {- 42 -} '( Cons   Lit.String (Layout t Draft Static )                          , 42 )
         , {- 43 -} '( Unify             (Layout t Draft Static )                          , 43 )
         , {- 44 -} '( Match             (Layout t Draft Static )                          , 44 )
         , {- 45 -} '( Acc    Lit.String (Layout t Draft Static )                          , 45 )
         , {- 46 -} '( App               (Layout t Draft Static )                          , 46 )
         , {- 47 -} '( Lam               (Layout t Draft Static )                          , 47 )
         , {- 48 -} '( Var               (Layout t Draft Dynamic)                          , 48 )
         , {- 49 -} '( Unify             (Layout t Draft Dynamic)                          , 49 )
         , {- 50 -} '( Match             (Layout t Draft Dynamic)                          , 50 )
         , {- 51 -} '( Acc               (Layout t Draft Dynamic) (Layout t Draft Dynamic) , 51 )
         , {- 52 -} '( App               (Layout t Draft Dynamic)                          , 52 )
         , {- 53 -} '( Cons              (Layout t Draft Dynamic) (Layout t Draft Dynamic) , 53 )
         , {- 54 -} '( Lam               (Layout t Draft Dynamic)                          , 54 )
         , {- 55 -} '( Native            (Layout t Draft Dynamic)                          , 55 )
         ]

#ifndef CachedTypeFamilies

FIXME
--type DecodeMap_RULE t = 'Map $ Zip (Layout_CACHE t) (Enumerate (Size (Layout_CACHE t)))
--CACHE_AS(DecodeMap_RULE, DecodeMap_GEN_CACHE)
--CHECK_EQ(DecodeMap_GEN_CACHE IM, DecodeMap_MANUAL_CACHE IM)
--type DecodeMap_CACHE t = DecodeMap_GEN_CACHE t

#else

type DecodeMap_CACHE t = DecodeMap_MANUAL_CACHE t

#endif

type instance DecodeMap (TermRecord gs vs t) = DecodeMap_CACHE t


-- === EncodeMap === --

type EncodeMap_MANUAL_CACHE t =
    'Map [ {-  9 -} '( Lit.Star                                                            , '[  9 , 0,1,2,3,4,5,6,7,8 ] )
         , {- 10 -} '( Lit.String                                                          , '[ 10 , 0,1,2,3,4,5,6,7,8 ] )
         , {- 11 -} '( Lit.Number                                                          , '[ 11 , 0,1,2,3,4,5,6,7,8 ] )
         , {- 12 -} '( Cons   Lit.String (Layout t Val   Static )                          , '[ 12 , 1,2,3,4,5,6,7,8   ] )
         , {- 13 -} '( Lam               (Layout t Val   Static )                          , '[ 13 , 1,2,3,4,5,6,7,8   ] )
         , {- 14 -} '( Cons              (Layout t Val   Dynamic) (Layout t Val   Dynamic) , '[ 14 , 2,4,6,8           ] )
         , {- 15 -} '( Lam               (Layout t Val   Dynamic)                          , '[ 15 , 2,4,6,8           ] )
         , {- 16 -} '( Cons   Lit.String (Layout t Thunk Static )                          , '[ 16 , 3,4,5,6,7,8       ] )
         , {- 17 -} '( Acc    Lit.String (Layout t Thunk Static )                          , '[ 17 , 3,4,5,6,7,8       ] )
         , {- 18 -} '( App               (Layout t Thunk Static )                          , '[ 18 , 3,4,5,6,7,8       ] )
         , {- 19 -} '( Lam               (Layout t Thunk Static )                          , '[ 19 , 3,4,5,6,7,8       ] )
         , {- 20 -} '( Native Lit.String                                                   , '[ 20 , 3,4,5,6,7,8       ] )
         , {- 21 -} '( Acc               (Layout t Thunk Dynamic) (Layout t Thunk Dynamic) , '[ 21 , 4,6,8             ] )
         , {- 22 -} '( App               (Layout t Thunk Dynamic)                          , '[ 22 , 4,6,8             ] )
         , {- 23 -} '( Cons              (Layout t Thunk Dynamic) (Layout t Thunk Dynamic) , '[ 23 , 4,6,8             ] )
         , {- 24 -} '( Lam               (Layout t Thunk Dynamic)                          , '[ 24 , 4,6,8             ] )
         , {- 25 -} '( Native            (Layout t Thunk Dynamic)                          , '[ 25 , 4,6,8             ] )
         , {- 26 -} '( Var    Lit.String                                                   , '[ 26 , 5,6,7,8           ] )
         , {- 27 -} '( Cons   Lit.String (Layout t Phrase Static )                          , '[ 27 , 5,6,7,8           ] )
         , {- 28 -} '( Unify             (Layout t Phrase Static )                          , '[ 28 , 5,6,7,8           ] )
         , {- 29 -} '( Match             (Layout t Phrase Static )                          , '[ 29 , 5,6,7,8           ] )
         , {- 30 -} '( Acc    Lit.String (Layout t Phrase Static )                          , '[ 30 , 5,6,7,8           ] )
         , {- 31 -} '( App               (Layout t Phrase Static )                          , '[ 31 , 5,6,7,8           ] )
         , {- 32 -} '( Lam               (Layout t Phrase Static )                          , '[ 32 , 5,6,7,8           ] )
         , {- 33 -} '( Var               (Layout t Phrase Dynamic)                          , '[ 33 , 6,8               ] )
         , {- 34 -} '( Unify             (Layout t Phrase Dynamic)                          , '[ 34 , 6,8               ] )
         , {- 35 -} '( Match             (Layout t Phrase Dynamic)                          , '[ 35 , 6,8               ] )
         , {- 36 -} '( Acc               (Layout t Phrase Dynamic) (Layout t Phrase Dynamic) , '[ 36 , 6,8               ] )
         , {- 37 -} '( App               (Layout t Phrase Dynamic)                          , '[ 37 , 6,8               ] )
         , {- 38 -} '( Cons              (Layout t Phrase Dynamic) (Layout t Phrase Dynamic) , '[ 38 , 6,8               ] )
         , {- 39 -} '( Lam               (Layout t Phrase Dynamic)                          , '[ 39 , 6,8               ] )
         , {- 40 -} '( Native            (Layout t Phrase Dynamic)                          , '[ 40 , 6,8               ] )
         , {- 41 -} '( Blank                                                               , '[ 41 , 7,8               ] )
         , {- 42 -} '( Cons   Lit.String (Layout t Draft Static )                          , '[ 42 , 7,8               ] )
         , {- 43 -} '( Unify             (Layout t Draft Static )                          , '[ 43 , 7,8               ] )
         , {- 44 -} '( Match             (Layout t Draft Static )                          , '[ 44 , 7,8               ] )
         , {- 45 -} '( Acc    Lit.String (Layout t Draft Static )                          , '[ 45 , 7,8               ] )
         , {- 46 -} '( App               (Layout t Draft Static )                          , '[ 46 , 7,8               ] )
         , {- 47 -} '( Lam               (Layout t Draft Static )                          , '[ 47 , 7,8               ] )
         , {- 48 -} '( Var               (Layout t Draft Dynamic)                          , '[ 48 , 8                 ] )
         , {- 49 -} '( Unify             (Layout t Draft Dynamic)                          , '[ 49 , 8                 ] )
         , {- 50 -} '( Match             (Layout t Draft Dynamic)                          , '[ 50 , 8                 ] )
         , {- 51 -} '( Acc               (Layout t Draft Dynamic) (Layout t Draft Dynamic) , '[ 51 , 8                 ] )
         , {- 52 -} '( App               (Layout t Draft Dynamic)                          , '[ 52 , 8                 ] )
         , {- 53 -} '( Cons              (Layout t Draft Dynamic) (Layout t Draft Dynamic) , '[ 53 , 8                 ] )
         , {- 54 -} '( Lam               (Layout t Draft Dynamic)                          , '[ 54 , 8                 ] )
         , {- 55 -} '( Native            (Layout t Draft Dynamic)                          , '[ 55 , 8                 ] )
         ]

#ifndef CachedTypeFamilies

FIXME
---- SubGroupRelations

--type family MapIndex els (cont :: [*]) where MapIndex '[]       cont = '[]
--                                             MapIndex (e ': es) cont = UnsafeIndex e cont ': MapIndex es cont

--type family SubGroups       g  where SubGroups       g         = (UniqueFix (SubGroups' g :: [*]) :: [*])
--type family SubGroups'      g  where SubGroups'      g         = GatherSubGroups (Groups g) <> Groups g
--type family GatherSubGroups gs where GatherSubGroups '[]       = ('[] :: [*])
--                                     GatherSubGroups (g ': gs) = SubGroups' g <> GatherSubGroups gs

--type family SubGroupRel    g  where SubGroupRel    g         = '(UnsafeIndex g (Layout_CACHE IM), MapIndex (SubGroups g :: [*]) (Layout_CACHE IM))
--type family MapSubGroupRel gs where MapSubGroupRel '[]       = ('[] :: [(Nat, [Nat])])
--                                    MapSubGroupRel (g ': gs) = SubGroupRel g ': MapSubGroupRel gs

--type SubGroupRelations_RULE = (MapSubGroupRel (GroupList IM) :: [(Nat, [Nat])])
--CACHE_AS(SubGroupRelations_RULE, SubGroupRelations)

---- SubGroupInvRelations

--type family InverseRel  arg rels where InverseRel arg rels = '(arg, InverseRel' arg rels)
--type family InverseRel' (arg :: Nat) (rels :: [(Nat, [Nat])]) where
--    InverseRel' a '[]                = '[]
--    InverseRel' a ( '(s, ts) ': rs ) = If (a `In` ts) '[s] '[] <> InverseRel' a rs

--type family MapInverseRel args rels where
--    MapInverseRel '[]       rels = '[]
--    MapInverseRel (a ': as) rels = InverseRel a rels ': MapInverseRel as rels

--type SubGroupInvRelations_RULE = (MapInverseRel (Enumerate (Size (GroupList IM))) SubGroupRelations :: [(Nat, [Nat])])
--CACHE_AS(SubGroupInvRelations_RULE, SubGroupInvRelations)

---- Relation expanders

--type        ExpandSubGroupRel  g  rels = g ': ExpandSubGroupRel' g rels
--type family ExpandSubGroupRel' g (rels :: [(Nat, [Nat])]) where
--    ExpandSubGroupRel' g '[] = '[]
--    ExpandSubGroupRel' g ( '(g, rels) ': rs ) = rels <> ExpandSubGroupRel' g rs
--    ExpandSubGroupRel' g ( r          ': rs ) =         ExpandSubGroupRel' g rs

--type family MapExpandSubGroupRel rels gs where
--    MapExpandSubGroupRel rels '[]       = '[]
--    MapExpandSubGroupRel rels (g ': gs) = ExpandSubGroupRel g rels <> MapExpandSubGroupRel rels gs

---- SubGroupInvRelations

--type family GroupsOf  v    where GroupsOf  v = UnsafeIndex v (Layout_CACHE IM) ': CatMaybes (GroupsOf' v (GroupList IM))
--type family GroupsOf' v gs where GroupsOf' v '[]       = '[]
--                                 GroupsOf' v (g ': gs) = If (v `In` X.Variants g) ('Just (UnsafeIndex g (GroupList IM))) 'Nothing ': GroupsOf' v gs

---- EncodeMapRel

--type EncodeMapRel a = UniqueFix (MapExpandSubGroupRel SubGroupInvRelations ( (GroupsOf a)))
--type family MapEncodeMapRel as where
--    MapEncodeMapRel '[] = '[]
--    MapEncodeMapRel (a ': as) = EncodeMapRel a ': MapEncodeMapRel as

---- Final rules

--type EncodeMap_RULE t = 'Map $ Zip (VariantList t) (MapEncodeMapRel (VariantList IM))
--CACHE_AS(EncodeMap_RULE, EncodeMap_GEN_CACHE)
--CHECK_EQ(EncodeMap_GEN_CACHE IM, EncodeMap_MANUAL_CACHE IM)
--type EncodeMap_CACHE t = EncodeMap_GEN_CACHE t

#else

type EncodeMap_CACHE t = EncodeMap_MANUAL_CACHE t

#endif

type instance EncodeMap (TermRecord gs vs t) = EncodeMap_CACHE t





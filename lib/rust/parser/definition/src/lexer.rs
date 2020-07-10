//! This module exposes the definition of lexer for the enso language.

use flexer::Flexer;


/// The definition of enso lexer that is responsible for lexing the enso source code.
/// It chunks the character stream into a (structured) token stream in order to make later
/// processing faster, and to identify blocks.
#[derive(Debug,Clone,Copy)]
pub struct Lexer {}

impl Flexer for Lexer {
    fn new() -> Self {
        Lexer{}
    }
}

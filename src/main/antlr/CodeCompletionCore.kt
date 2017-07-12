import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.atn.*
import org.antlr.v4.runtime.misc.IntervalSet
import java.util.*
import kotlin.collections.HashSet

/*
 * Based on https://github.com/mike-lischke/antlr4-c3 by Mike Lischke
 */

//'use strict';
//
//import { Parser, Vocabulary, Token, TokenStream, RuleContext, ParserRuleContext } from 'antlr4ts';
//import { ATN, ATNState, ATNStateType, Transition, TransitionType, PredicateTransition, RuleTransition, RuleStartState } from 'antlr4ts/atn';
//import { IntervalSet } from 'antlr4ts/misc';
//
//export type TokenList = number[];
//export type RuleList = number[];

typealias TokenList = MutableList<Int>
typealias RuleList = MutableList<Int>

//
//// All the candidates which have been found. Tokens and rules are separated (both use a numeric value).
//// Token entries include a list of tokens that directly follow them (see also the "following" member in the FollowSetWithPath class).
//export class CandidatesCollection {
//    public tokens: Map<number, TokenList> = new Map();
//    public rules: Map<number, RuleList> = new Map();
//};

// All the candidates which have been found. Tokens and rules are separated (both use a numeric value).
// Token entries include a list of tokens that directly follow them (see also the "following" member in the FollowSetWithPath class).
class CandidatesCollection() {
    var tokens: MutableMap<Int, TokenList> = HashMap()
    var rules: MutableMap<Int, RuleList> = HashMap()
}


//// A record for a follow set along with the path at which this set was found.
//// If there is only a single symbol in the interval set then we also collect and store tokens which follow
//// this symbol directly in its rule (i.e. there is no intermediate rule transition). Only single label transitions
//// are considered. This is useful if you have a chain of tokens which can be suggested as a whole, because there is
//// a fixed sequence in the grammar.
//class FollowSetWithPath {
//    public intervals: IntervalSet;
//    public path: RuleList = [];
//    public following: TokenList = [];
//};

// A record for a follow set along with the path at which this set was found.
// If there is only a single symbol in the interval set then we also collect and store tokens which follow
// this symbol directly in its rule (i.e. there is no intermediate rule transition). Only single label transitions
// are considered. This is useful if you have a chain of tokens which can be suggested as a whole, because there is
// a fixed sequence in the grammar.

class FollowSetWithPath {
    var intervals = IntervalSet()
    var path : RuleList = LinkedList()
    var following : TokenList = LinkedList()
}


//// A list of follow sets (for a given state number) + all of them combined for quick hit tests.
//// This data is static in nature (because the used ATN states are part of a static struct: the ATN).
//// Hence it can be shared between all C3 instances, however it dependes on the actual parser class (type).
//class FollowSetsHolder {
//    public sets: FollowSetWithPath[];
//    public combined: IntervalSet
//};


// A list of follow sets (for a given state number) + all of them combined for quick hit tests.
// This data is static in nature (because the used ATN states are part of a static struct: the ATN).
// Hence it can be shared between all C3 instances, however it dependes on the actual parser class (type).
class FollowSetsHolder {
    var sets : MutableList<FollowSetWithPath> = LinkedList<FollowSetWithPath>()
    var combined = IntervalSet()
}

//type FollowSetsPerState = Map<number, FollowSetsHolder>;

typealias FollowSetsPerState = MutableMap<Int, FollowSetsHolder>


//// Token stream position info after a rule was processed.
//type RuleEndStatus = Set<number>;

// Token stream position info after a rule was processed.
typealias RuleEndStatus = MutableSet<Int>


//class PipelineEntry {
//    state: ATNState;
//    tokenIndex: number;
//};

data class PipelineEntry(val state: ATNState, val tokenIndex: Int)


//// The main class for doing the collection process.
//export class CodeCompletionCore {

// The main class for doing the collection process.
class CodeCompletionCore(val parser: Parser) {

//    // Debugging options. Print human readable ATN state and other info.
//    public showResult = false;                 // Not dependent on showDebugOutput. Prints the collected rules + tokens to terminal.
//    public showDebugOutput = false;            // Enables printing ATN state info to terminal.
//    public debugOutputWithTransitions = false; // Only relevant when showDebugOutput is true. Enables transition printing for a state.
//    public showRuleStack = false;              // Also depends on showDebugOutput. Enables call stack printing for each rule recursion.

    // Debugging options. Print human readable ATN state and other info.
    val showResult = false                 // Not dependent on showDebugOutput. Prints the collected rules + tokens to terminal.
    val showDebugOutput = false            // Enables printing ATN state info to terminal.
    val debugOutputWithTransitions = false // Only relevant when showDebugOutput is true. Enables transition printing for a state.
    val showRuleStack = false              // Also depends on showDebugOutput. Enables call stack printing for each rule recursion.

//    // Tailoring of the result.
//    public ignoredTokens: Set<number>;        // Tokens which should not appear in the candidates set.
//    public preferredRules: Set<number>;       // Rules which replace any candidate token they contain.
//    // This allows to return descriptive rules (e.g. className, instead of ID/identifier).

    // Tailoring of the result.
    val ignoredTokens = HashSet<Int>()        // Tokens which should not appear in the candidates set.
    val preferredRules = HashSet<Int>()       // Rules which replace any candidate token they contain.
    // This allows to return descriptive rules (e.g. className, instead of ID/identifier).

//
//    private parser: Parser;
//    private atn: ATN;
//    private vocabulary: Vocabulary;
//    private ruleNames: string[];
//    private tokens: TokenList;

    // parser is in the primary constructor
    private val atn = parser.atn
    private val vocabulary = parser.vocabulary
    private val ruleNames = parser.ruleNames
    private var tokens : TokenList = LinkedList()

//
//    private tokenStartIndex: number = 0;
//
//    private statesProcessed: number = 0;

    private var tokenStartIndex = 0
    private var statesProcessed = 0
//
//    // A mapping of rule index + token stream position to end token positions.
//    // A rule which has been visited before with the same input position will always produce the same output positions.
//    private shortcutMap: Map<number, Map<number, RuleEndStatus>> = new Map();
//    private candidates: CandidatesCollection = new CandidatesCollection(); // The collected candidates (rules and tokens).

    // A mapping of rule index + token stream position to end token positions.
    // A rule which has been visited before with the same input position will always produce the same output positions.
    private val shortcutMap : MutableMap<Int, MutableMap<Int, RuleEndStatus>> = HashMap()
    private val candidates = CandidatesCollection()


//    private static followSetsByATN: Map<string, FollowSetsPerState> = new Map();

    companion object {
        val followSetsByATN: MutableMap<String, FollowSetsPerState> = HashMap()
    }


//    constructor(parser: Parser) {
//        this.parser = parser;
//        this.atn = parser.atn;
//        this.vocabulary = parser.vocabulary;
//        this.ruleNames = parser.ruleNames;
//        this.ignoredTokens = new Set();
//        this.preferredRules = new Set();
//    }

// Kotlin: corresponds to the primary constructor

//
//    /**
//     * This is the main entry point. The caret token index specifies the token stream index for the token which currently
//     * covers the caret (or any other position you want to get code completion candidates for).
//     * Optionally you can pass in a parser rule context which limits the ATN walk to only that or called rules. This can significantly
//     * speed up the retrieval process but might miss some candidates (if they are outside of the given context).
//     */
//    public collectCandidates(caretTokenIndex: number, context?: ParserRuleContext): CandidatesCollection {

    /**
     * This is the main entry point. The caret token index specifies the token stream index for the token which currently
     * covers the caret (or any other position you want to get code completion candidates for).
     * Optionally you can pass in a parser rule context which limits the ATN walk to only that or called rules. This can significantly
     * speed up the retrieval process but might miss some candidates (if they are outside of the given context).
     */
    fun collectCandidates(caretTokenIndex: Int, context: ParserRuleContext?): CandidatesCollection {

//        this.shortcutMap.clear();
//        this.candidates.rules.clear();
//        this.candidates.tokens.clear();
//        this.statesProcessed = 0;

        this.shortcutMap.clear()
        this.candidates.rules.clear()
        this.candidates.tokens.clear()
        this.statesProcessed = 0

//        this.tokenStartIndex = context ? context.start.tokenIndex : 0;
//        let tokenStream: TokenStream = this.parser.inputStream;

        this.tokenStartIndex = context?.start?.tokenIndex ?: 0
        val tokenStream: TokenStream = this.parser.inputStream

//        let currentIndex = tokenStream.index;
//        tokenStream.seek(this.tokenStartIndex);
//        this.tokens = [];
//        let offset = 1;
//        while (true) {
//            let token = tokenStream.LT(offset++);
//            this.tokens.push(token.type);
//            if (token.tokenIndex >= caretTokenIndex || token.type == Token.EOF)
//                break;
//        }
//        tokenStream.seek(currentIndex);

        val currentIndex = tokenStream.index()
        tokenStream.seek(this.tokenStartIndex)
        this.tokens = LinkedList()
        var offset = 1
        while (true) {
            val token = tokenStream.LT(offset++);
            this.tokens.add(token.type)
            if (token.tokenIndex >= caretTokenIndex || token.type == Token.EOF) {
                break
            }
        }
        tokenStream.seek(currentIndex)


//        let callStack: number[] = [];
//        let startRule = context ? context.ruleIndex : 0;
//        this.processRule(this.atn.ruleToStartState[startRule], 0, callStack, "");
//
//        if (this.showResult)
//            console.log("States processed: " + this.statesProcessed);

        val callStack: MutableList<Int> = LinkedList<Int>();
        val startRule = context?.ruleIndex ?: 0
        this.processRule(this.atn.ruleToStartState[startRule], 0, callStack, "")

        if (this.showResult) {
            println("States processed: $statesProcessed")
        }

//        if (this.showResult) {
//            console.log("\n\nCollected rules:\n");
//            for (let rule of this.candidates.rules) {
//                let path = "";
//                for (let token of rule[1]) {
//                path += this.ruleNames[token] + " ";
//            }
//                console.log(this.ruleNames[rule[0]] + ", path: ", path);
//            }
//
//            let sortedTokens: Set<string> = new Set();
//            for (let token of this.candidates.tokens) {
//                let value: string = this.vocabulary.getDisplayName(token[0]);
//                for (let following of token[1])
//                value += " " + this.vocabulary.getDisplayName(following);
//                sortedTokens.add(value);
//            }
//
//            console.log("\n\nCollected tokens:\n");
//            for (let symbol of sortedTokens) {
//                console.log(symbol);
//            }
//            console.log("\n\n");
//        }

        // TODO to be translated

//        return this.candidates;
        return this.candidates
//    }
    }


//    /**
//     * Check if the predicate associated with the given transition evaluates to true.
//     */
//    private checkPredicate(transition: PredicateTransition): boolean {
//        return transition.predicate.eval(this.parser, ParserRuleContext.emptyContext());
//    }

    /**
     * Check if the predicate associated with the given transition evaluates to true.
     */
    private fun checkPredicate(transition: PredicateTransition): Boolean {
        return transition.predicate.eval(this.parser, ParserRuleContext.EMPTY)
    }


//    /**
//     * Walks the rule chain upwards to see if that matches any of the preferred rules.
//     * If found, that rule is added to the collection candidates and true is returned.
//     */
//    private translateToRuleIndex(ruleStack: RuleList): boolean {
//        if (this.preferredRules.size == 0)
//            return false;
//
//        // Loop over the rule stack from highest to lowest rule level. This way we properly handle the higher rule
//        // if it contains a lower one that is also a preferred rule.
//        for (let i = 0; i < ruleStack.length; ++i) {
//            if (this.preferredRules.has(ruleStack[i])) {
//                // Add the rule to our candidates list along with the current rule path,
//                // but only if there isn't already an entry like that.
//                let path = ruleStack.slice(0, i);
//                let addNew = true;
//                for (let rule of this.candidates.rules) {
//                    if (rule[0] != ruleStack[i] || rule[1].length != path.length)
//                        continue;
//                    // Found an entry for this rule. Same path? If so don't add a new (duplicate) entry.
//                    if (path.every((v, j) => v === rule[1][j])) {
//                    addNew = false;
//                    break;
//                }
//                }
//
//                if (addNew) {
//                    this.candidates.rules.set(ruleStack[i], path);
//                    if (this.showDebugOutput)
//                        console.log("=====> collected: ", this.ruleNames[i]);
//                }
//                return true;
//            }
//        }
//
//        return false;
//    }

    /**
     * Walks the rule chain upwards to see if that matches any of the preferred rules.
     * If found, that rule is added to the collection candidates and true is returned.
     */
    private fun translateToRuleIndex(ruleStack: RuleList): Boolean {
        if (this.preferredRules.size == 0)
            return false

        // Loop over the rule stack from highest to lowest rule level. This way we properly handle the higher rule
        // if it contains a lower one that is also a preferred rule.

        for (i in ruleStack.indices) {
            if (this.preferredRules.contains(ruleStack[i])) {
                // Add the rule to our candidates list along with the current rule path,
                // but only if there isn't already an entry like that.
                var path = ruleStack.subList(0, i)
                var addNew = true;
                for (rule in this.candidates.rules) {
                    if (rule.key != ruleStack[i] || rule.value.size != path.size)
                        continue
                    // TODO translate
                    // Found an entry for this rule. Same path? If so don't add a new (duplicate) entry.
                    //if (path.all {v, j -> v === rule[1][j] }) {
                    //    addNew = false
                    //    break
                    //}
                }

                if (addNew) {
                    this.candidates.rules[ruleStack[i]] = path
                    if (this.showDebugOutput)
                        println("=====> collected: ${this.ruleNames[i]}")
                }
                return true
            }
        }

        return false
    }


//    /**
//     * This method follows the given transition and collects all symbols within the same rule that directly follow it
//     * without intermediate transitions to other rules and only if there is a single symbol for a transition.
//     */
//    private getFollowingTokens(transition: Transition): number[] {
//        let result: number[] = [];
//
//        let seen: ATNState[] = [];
//        let pipeline: ATNState[] = [transition.target];
//
//        while (pipeline.length > 0) {
//            let state = pipeline.pop();
//
//            for (let transition of state!.getTransitions()) {
//                if (transition.serializationType == TransitionType.ATOM) {
//                    if (!transition.isEpsilon) {
//                        let list = transition.label!.toList();
//                        if (list.length == 1 && !this.ignoredTokens.has(list[0])) {
//                            result.push(list[0]);
//                            pipeline.push(transition.target);
//                        }
//                    } else {
//                        pipeline.push(transition.target);
//                    }
//                }
//            }
//        }
//
//        return result;
//    }

    /**
     * This method follows the given transition and collects all symbols within the same rule that directly follow it
     * without intermediate transitions to other rules and only if there is a single symbol for a transition.
     */
    private fun getFollowingTokens(transition: Transition): TokenList {
        var result = LinkedList<Int>()

        var seen = LinkedList<ATNState>()
        var pipeline: MutableList<ATNState> = LinkedList<ATNState>()
        pipeline.add(transition.target)

        while (pipeline.size > 0) {
            var state = pipeline.removeAt(pipeline.size - 1)

            for (transition in state.transitions) {
                if (transition.serializationType == Transition.ATOM) {
                    if (!transition.isEpsilon) {
                        var list = transition.label().toList()
                        if (list.size == 1 && !this.ignoredTokens.contains(list[0])) {
                            result.push(list[0]);
                            pipeline.add(transition.target);
                        }
                    } else {
                        pipeline.add(transition.target);
                    }
                }
            }
        }

        return result;
    }

//    /**
//     * Entry point for the recursive follow set collection function.
//     */
//    private determineFollowSets(start: ATNState, stop: ATNState): FollowSetWithPath[] {
//        let result: FollowSetWithPath[] = [];
//        let seen: Set<ATNState> = new Set();
//        let ruleStack: number[] = [];
//        this.collectFollowSets(start, stop, result, seen, ruleStack);
//
//        return result;
//    }

    /**
     * Entry point for the recursive follow set collection function.
     */
    private fun determineFollowSets(start: ATNState, stop: ATNState): MutableList<FollowSetWithPath> {
        var result: MutableList<FollowSetWithPath> = LinkedList()
        var seen: MutableSet<ATNState> = HashSet()
        var ruleStack: MutableList<Int> = LinkedList()
        this.collectFollowSets(start, stop, result, seen, ruleStack)

        return result
    }

//
//    /**
//     * Collects possible tokens which could be matched following the given ATN state. This is essentially the same
//     * algorithm as used in the LL1Analyzer class, but here we consider predicates also and use no parser rule context.
//     */
//    private collectFollowSets(s: ATNState, stopState: ATNState, followSets: FollowSetWithPath[], seen: Set<ATNState>, ruleStack: number[]) {
//
//        if (seen.has(s))
//            return;
//
//        seen.add(s);
//
//        if (s == stopState || s.stateType == ATNStateType.RULE_STOP) {
//            let set = new FollowSetWithPath();
//            set.intervals = IntervalSet.of(Token.EPSILON);
//            set.path = ruleStack.slice();
//            followSets.push(set);
//            return;
//        }
//
//        for (let transition of s.getTransitions()) {
//            if (transition.serializationType == TransitionType.RULE) {
//                let ruleTransition: RuleTransition = transition as RuleTransition;
//                if (ruleStack.indexOf(ruleTransition.target.ruleIndex) != -1)
//                    continue;
//
//                ruleStack.push(ruleTransition.target.ruleIndex);
//                this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
//                ruleStack.pop();
//
//            } else if (transition.serializationType == TransitionType.PREDICATE) {
//                if (this.checkPredicate(transition as PredicateTransition))
//                    this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
//            } else if (transition.isEpsilon) {
//                this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
//            } else if (transition.serializationType == TransitionType.WILDCARD) {
//                let set = new FollowSetWithPath();
//                set.intervals = IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType);
//                set.path = ruleStack.slice();
//                followSets.push(set);
//            } else {
//                let label = transition.label;
//                if (label && label.size > 0) {
//                    if (transition.serializationType == TransitionType.NOT_SET) {
//                        label = label.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
//                    }
//                    let set = new FollowSetWithPath();
//                    set.intervals = label;
//                    set.path = ruleStack.slice();
//                    set.following = this.getFollowingTokens(transition);
//                    followSets.push(set);
//                }
//            }
//        }
//    }

    /**
     * Collects possible tokens which could be matched following the given ATN state. This is essentially the same
     * algorithm as used in the LL1Analyzer class, but here we consider predicates also and use no parser rule context.
     */
    private fun collectFollowSets(s: ATNState, stopState: ATNState, followSets: MutableList<FollowSetWithPath>, seen: MutableSet<ATNState>, ruleStack: MutableList<Int>) {

        if (seen.contains(s))
            return

        seen.add(s)

        if (s == stopState || s.stateType == ATNState.RULE_STOP) {
            var set = FollowSetWithPath()
            set.intervals = IntervalSet.of(Token.EPSILON);
            set.path = LinkedList(ruleStack)
            followSets.add(set)
            return
        }

        for (transition in s.transitions) {
            if (transition.serializationType == Transition.RULE) {
                var ruleTransition: RuleTransition = transition as RuleTransition
                if (ruleStack.indexOf(ruleTransition.target.ruleIndex) != -1)
                    continue;

                ruleStack.add(ruleTransition.target.ruleIndex);
                this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
                ruleStack.pop()

            } else if (transition.serializationType == Transition.PREDICATE) {
                if (this.checkPredicate(transition as PredicateTransition))
                    this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
            } else if (transition.isEpsilon) {
                this.collectFollowSets(transition.target, stopState, followSets, seen, ruleStack);
            } else if (transition.serializationType == Transition.WILDCARD) {
                var set = FollowSetWithPath()
                set.intervals = IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType);
                set.path = LinkedList(ruleStack)
                followSets.add(set)
            } else {
                var label = transition.label()
                if (label.intervals.isNotEmpty()) {
                    if (transition.serializationType == Transition.NOT_SET) {
                        label = label.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
                    }
                    var set = FollowSetWithPath()
                    set.intervals = label;
                    set.path = LinkedList(ruleStack)
                    set.following = this.getFollowingTokens(transition)
                    followSets.add(set)
                }
            }
        }
    }

//
//    /**
//     * Walks the ATN for a single rule only. It returns the token stream position for each path that could be matched in this rule.
//     * The result can be empty in case we hit only non-epsilon transitions that didn't match the current input or if we
//     * hit the caret position.
//     */
//    private processRule(startState: ATNState, tokenIndex: number, callStack: number[], indentation: string): RuleEndStatus {
//
//        // Start with rule specific handling before going into the ATN walk.
//
//        // Check first if we've taken this path with the same input before.
//        let positionMap = this.shortcutMap.get(startState.ruleIndex);
//        if (!positionMap) {
//            positionMap = new Map();
//            this.shortcutMap.set(startState.ruleIndex, positionMap);
//        } else {
//            if (positionMap.has(tokenIndex)) {
//                if (this.showDebugOutput) {
//                    console.log("=====> shortcut");
//                }
//                return positionMap.get(tokenIndex)!;
//            }
//        }
//
//        let result: RuleEndStatus = new Set<number>();
//
//        // For rule start states we determine and cache the follow set, which gives us 3 advantages:
//        // 1) We can quickly check if a symbol would be matched when we follow that rule. We can so check in advance
//        //    and can save us all the intermediate steps if there is no match.
//        // 2) We'll have all symbols that are collectable already together when we are at the caret when entering a rule.
//        // 3) We get this lookup for free with any 2nd or further visit of the same rule, which often happens
//        //    in non trivial grammars, especially with (recursive) expressions and of course when invoking code completion
//        //    multiple times.
//        let setsPerState = CodeCompletionCore.followSetsByATN.get(this.parser.constructor.name);
//        if (!setsPerState) {
//            setsPerState = new Map();
//            CodeCompletionCore.followSetsByATN.set(this.parser.constructor.name, setsPerState);
//        }
//
//        let followSets = setsPerState.get(startState.stateNumber);
//        if (!followSets) {
//            followSets = new FollowSetsHolder();
//            setsPerState.set(startState.stateNumber, followSets);
//            let stop = this.atn.ruleToStopState[startState.ruleIndex];
//            followSets.sets = this.determineFollowSets(startState, stop);
//
//            // Sets are split by path to allow translating them to preferred rules. But for quick hit tests
//            // it is also useful to have a set with all symbols combined.
//            let combined = new IntervalSet();
//            for (let set of followSets.sets)
//            combined.addAll(set.intervals);
//            followSets.combined = combined;
//        }
//
//        callStack.push(startState.ruleIndex);
//        let currentSymbol = this.tokens[tokenIndex];
//
//        if (tokenIndex >= this.tokens.length - 1) { // At caret?
//            if (this.preferredRules.has(startState.ruleIndex)) {
//                // No need to go deeper when collecting entries and we reach a rule that we want to collect anyway.
//                this.translateToRuleIndex(callStack);
//            } else {
//                // Convert all follow sets to either single symbols or their associated preferred rule and add
//                // the result to our candidates list.
//                for (let set of followSets.sets) {
//                    let fullPath = callStack.slice();
//                    fullPath.push(...set.path);
//                    if (!this.translateToRuleIndex(fullPath)) {
//                        for (let symbol of set.intervals.toList())
//                        if (!this.ignoredTokens.has(symbol)) {
//                            if (this.showDebugOutput) {
//                                console.log("=====> collected: ", this.vocabulary.getDisplayName(symbol));
//                            }
//                            if (!this.candidates.tokens.has(symbol))
//                                this.candidates.tokens.set(symbol, set.following); // Following is empty if there is more than one entry in the set.
//                            else {
//                                // More than one following list for the same symbol.
//                                if (this.candidates.tokens.get(symbol) != set.following)
//                                    this.candidates.tokens.set(symbol, []);
//                            }
//                        }
//                    }
//                }
//            }
//
//            callStack.pop();
//            return result;
//
//        } else {
//            // Process the rule if we either could pass it without consuming anything (epsilon transition)
//            // or if the current input symbol will be matched somewhere after this entry point.
//            // Otherwise stop here.
//            if (!followSets.combined.contains(Token.EPSILON) && !followSets.combined.contains(currentSymbol)) {
//                callStack.pop();
//                return result;
//            }
//        }
//
//        // The current state execution pipeline contains all yet-to-be-processed ATN states in this rule.
//        // For each such state we store the token index + a list of rules that lead to it.
//        let statePipeline: PipelineEntry[] = [];
//        let currentEntry;
//
//        // Bootstrap the pipeline.
//        statePipeline.push({ state: startState, tokenIndex: tokenIndex });
//
//        while (statePipeline.length > 0) {
//            currentEntry = statePipeline.pop()!;
//            ++this.statesProcessed;
//
//            currentSymbol = this.tokens[currentEntry.tokenIndex];
//
//            let atCaret = currentEntry.tokenIndex >= this.tokens.length - 1;
//            if (this.showDebugOutput) {
//                this.printDescription(indentation, currentEntry.state, this.generateBaseDescription(currentEntry.state), currentEntry.tokenIndex);
//                if (this.showRuleStack)
//                    this.printRuleState(callStack);
//            }
//
//            switch (currentEntry.state.stateType) {
//                case ATNStateType.RULE_START: // Happens only for the first state in this rule, not subrules.
//                indentation += "  ";
//                break;
//
//                case ATNStateType.RULE_STOP: {
//                    // Record the token index we are at, to report it to the caller.
//                    result.add(currentEntry.tokenIndex);
//                    continue;
//                }
//
//                default:
//                break;
//            }
//
//            let transitions = currentEntry.state.getTransitions();
//            for (let transition of transitions) {
//                switch (transition.serializationType) {
//                    case TransitionType.RULE: {
//                        let endStatus = this.processRule(transition.target, currentEntry.tokenIndex, callStack, indentation);
//                        for (let position of endStatus) {
//                        statePipeline.push({ state: (<RuleTransition>transition).followState, tokenIndex: position });
//                    }
//                        break;
//                    }
//
//                    case TransitionType.PREDICATE: {
//                        if (this.checkPredicate(transition as PredicateTransition))
//                            statePipeline.push({ state: transition.target, tokenIndex: currentEntry.tokenIndex });
//                        break;
//                    }
//
//                    case TransitionType.WILDCARD: {
//                        if (atCaret) {
//                            if (!this.translateToRuleIndex(callStack)) {
//                                for (let token of IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType).toList())
//                                if (!this.ignoredTokens.has(token))
//                                    this.candidates.tokens.set(token, []);
//                            }
//                        } else {
//                            statePipeline.push({ state: transition.target, tokenIndex: currentEntry.tokenIndex + 1 });
//                        }
//                        break;
//                    }
//
//                    default: {
//                    if (transition.isEpsilon) {
//                        // Jump over simple states with a single outgoing epsilon transition.
//                        statePipeline.push({ state: transition.target, tokenIndex: currentEntry.tokenIndex });
//                        continue;
//                    }
//
//                    let set = transition.label;
//                    if (set && set.size > 0) {
//                        if (transition.serializationType == TransitionType.NOT_SET) {
//                            set = set.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
//                        }
//                        if (atCaret) {
//                            if (!this.translateToRuleIndex(callStack)) {
//                                let list = set.toList();
//                                let addFollowing = list.length == 1;
//                                for (let symbol of list)
//                                if (!this.ignoredTokens.has(symbol)) {
//                                    if (this.showDebugOutput)
//                                        console.log("=====> collected: ", this.vocabulary.getDisplayName(symbol));
//
//                                    if (addFollowing)
//                                        this.candidates.tokens.set(symbol, this.getFollowingTokens(transition));
//                                    else
//                                        this.candidates.tokens.set(symbol, []);
//                                }
//                            }
//                        } else {
//                            if (set.contains(currentSymbol)) {
//                                if (this.showDebugOutput)
//                                    console.log("=====> consumed: ", this.vocabulary.getDisplayName(currentSymbol));
//                                statePipeline.push({ state: transition.target, tokenIndex: currentEntry.tokenIndex + 1 });
//                            }
//                        }
//                    }
//                }
//                }
//            }
//        }
//
//        callStack.pop();
//
//        // Cache the result, for later lookup to avoid duplicate walks.
//        positionMap.set(tokenIndex, result);
//
//        return result;
//    }

    /**
     * Walks the ATN for a single rule only. It returns the token stream position for each path that could be matched in this rule.
     * The result can be empty in case we hit only non-epsilon transitions that didn't match the current input or if we
     * hit the caret position.
     */
    private fun processRule(startState: ATNState, tokenIndex: Int, callStack: MutableList<Int>, _indentation: String): RuleEndStatus {

        var indentation : String = _indentation

        // Start with rule specific handling before going into the ATN walk.

        // Check first if we've taken this path with the same input before.
        var positionMap = this.shortcutMap[startState.ruleIndex]
        if (positionMap == null) {
            positionMap = HashMap()
            this.shortcutMap.set(startState.ruleIndex, positionMap)
        } else {
            if (positionMap.contains(tokenIndex)) {
                if (this.showDebugOutput) {
                    println("=====> shortcut");
                }
                return positionMap[tokenIndex]!!
            }
        }

        var result: RuleEndStatus = HashSet<Int>()

        // For rule start states we determine and cache the follow set, which gives us 3 advantages:
        // 1) We can quickly check if a symbol would be matched when we follow that rule. We can so check in advance
        //    and can save us all the intermediate steps if there is no match.
        // 2) We'll have all symbols that are collectable already together when we are at the caret when entering a rule.
        // 3) We get this lookup for free with any 2nd or further visit of the same rule, which often happens
        //    in non trivial grammars, especially with (recursive) expressions and of course when invoking code completion
        //    multiple times.
        var setsPerState = CodeCompletionCore.followSetsByATN[this.parser.javaClass.simpleName]
        if (setsPerState == null) {
            setsPerState = HashMap()
            CodeCompletionCore.followSetsByATN[this.parser.javaClass.simpleName] = setsPerState
        }

        var followSets = setsPerState.get(startState.stateNumber);
        if (followSets == null) {
            followSets = FollowSetsHolder();
            setsPerState.set(startState.stateNumber, followSets);
            var stop = this.atn.ruleToStopState[startState.ruleIndex];
            followSets.sets = this.determineFollowSets(startState, stop);

            // Sets are split by path to allow translating them to preferred rules. But for quick hit tests
            // it is also useful to have a set with all symbols combined.
            var combined = IntervalSet()
            for (set in followSets.sets)
            combined.addAll(set.intervals);
            followSets.combined = combined;
        }

        callStack.push(startState.ruleIndex);
        var currentSymbol = this.tokens[tokenIndex];

        if (tokenIndex >= this.tokens.size - 1) { // At caret?
            if (this.preferredRules.contains(startState.ruleIndex)) {
                // No need to go deeper when collecting entries and we reach a rule that we want to collect anyway.
                this.translateToRuleIndex(callStack);
            } else {
                // Convert all follow sets to either single symbols or their associated preferred rule and add
                // the result to our candidates list.
                for (set in followSets.sets) {
                    var fullPath = LinkedList(callStack)
                    fullPath.addAll(set.path)
                    if (!this.translateToRuleIndex(fullPath)) {
                        for (symbol in set.intervals.toList())
                        if (!this.ignoredTokens.contains(symbol)) {
                            if (this.showDebugOutput) {
                                println("=====> collected: ${this.vocabulary.getDisplayName(symbol)}")
                            }
                            if (!this.candidates.tokens.contains(symbol))
                                this.candidates.tokens.set(symbol, set.following); // Following is empty if there is more than one entry in the set.
                            else {
                                // More than one following list for the same symbol.
                                if (this.candidates.tokens.get(symbol) != set.following)
                                    this.candidates.tokens.set(symbol, LinkedList());
                            }
                        }
                    }
                }
            }

            callStack.pop()
            return result

        } else {
            // Process the rule if we either could pass it without consuming anything (epsilon transition)
            // or if the current input symbol will be matched somewhere after this entry point.
            // Otherwise stop here.
            if (!followSets.combined.contains(Token.EPSILON) && !followSets.combined.contains(currentSymbol)) {
                callStack.pop();
                return result;
            }
        }

        // The current state execution pipeline contains all yet-to-be-processed ATN states in this rule.
        // For each such state we store the token index + a list of rules that lead to it.
        var statePipeline = LinkedList<PipelineEntry>()
        var currentEntry : PipelineEntry? = null

        // Bootstrap the pipeline.
        statePipeline.push(PipelineEntry(startState, tokenIndex ))

        while (statePipeline.size > 0) {
            currentEntry = statePipeline.pop()
            ++this.statesProcessed;

            currentSymbol = this.tokens[currentEntry.tokenIndex]

            var atCaret = currentEntry!!.tokenIndex >= this.tokens.size - 1;
            if (this.showDebugOutput) {
                this.printDescription(indentation, currentEntry.state, this.generateBaseDescription(currentEntry.state), currentEntry.tokenIndex);
                if (this.showRuleStack)
                    this.printRuleState(callStack);
            }

            when (currentEntry.state.stateType) {
                ATNState.RULE_START -> { // Happens only for the first state in this rule, not subrules.
                    indentation += "  ";
                }
                ATNState.RULE_STOP -> {
                    // Record the token index we are at, to report it to the caller.
                    result.add(currentEntry.tokenIndex);
                }
            }

            var transitions = currentEntry.state.getTransitions();
            myFor@ for (transition in transitions) {
                when (transition.serializationType) {
                    Transition.RULE -> {
                        var endStatus = this.processRule(transition.target, currentEntry.tokenIndex, callStack, indentation);
                        for (position in endStatus) {
                        statePipeline.push(PipelineEntry((transition as RuleTransition).followState, position ))
                    }
                    }

                    Transition.PREDICATE -> {
                        if (this.checkPredicate(transition as PredicateTransition)) {
                            statePipeline.push(PipelineEntry(transition.target, currentEntry.tokenIndex ))
                        }
                    }

                    Transition.WILDCARD -> {
                        if (atCaret) {
                            if (!this.translateToRuleIndex(callStack)) {
                                for (token in IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType).toList())
                                if (!this.ignoredTokens.contains(token))
                                    this.candidates.tokens.set(token, LinkedList());
                            }
                        } else {
                            statePipeline.push(PipelineEntry(transition.target, currentEntry.tokenIndex + 1 ))
                        }
                    }

                    else -> {
                        if (transition.isEpsilon) {
                            // Jump over simple states with a single outgoing epsilon transition.
                            statePipeline.push(PipelineEntry(transition.target, currentEntry.tokenIndex));
                            continue@myFor
                        }

                        var set = transition.label()
                        if (set.intervals.isNotEmpty()) {
                            if (transition.serializationType == Transition.NOT_SET) {
                                set = set.complement(IntervalSet.of(Token.MIN_USER_TOKEN_TYPE, this.atn.maxTokenType));
                            }
                            if (atCaret) {
                                if (!this.translateToRuleIndex(callStack)) {
                                    var list = set.toList();
                                    var addFollowing = list.size == 1;
                                    for (symbol in list)
                                    if (!this.ignoredTokens.contains(symbol)) {
                                        if (this.showDebugOutput)
                                            println("=====> collected: ${this.vocabulary.getDisplayName(symbol)}")

                                        if (addFollowing) {
                                            this.candidates.tokens[symbol] = this.getFollowingTokens(transition)
                                        } else {
                                            this.candidates.tokens[symbol] = LinkedList();
                                        }
                                    }
                                }
                            } else {
                                if (set.contains(currentSymbol)) {
                                    if (this.showDebugOutput) {
                                        println("=====> consumed: ${this.vocabulary.getDisplayName(currentSymbol)}")
                                    }
                                    statePipeline.push(PipelineEntry(transition.target, currentEntry.tokenIndex + 1 ))
                                }
                            }
                        }
                    }
                }
            }
        }

        callStack.pop()

        // Cache the result, for later lookup to avoid duplicate walks.
        positionMap.set(tokenIndex, result)

        return result
    }



//    private atnStateTypeMap: string[] = [
//    "invalid",
//    "basic",
//    "rule start",
//    "block start",
//    "plus block start",
//    "star block start",
//    "token start",
//    "rule stop",
//    "block end",
//    "star loop back",
//    "star loop entry",
//    "plus loop back",
//    "loop end"
//    ]

    private val atnStateTypeMap: List<String> = listOf(
    "invalid",
    "basic",
    "rule start",
    "block start",
    "plus block start",
    "star block start",
    "token start",
    "rule stop",
    "block end",
    "star loop back",
    "star loop entry",
    "plus loop back",
    "loop end"
    )


//    private generateBaseDescription(state: ATNState): string {
//        let stateValue = state.stateNumber == ATNState.INVALID_STATE_NUMBER ? "Invalid" : state.stateNumber;
//        return "[" + stateValue + " " + this.atnStateTypeMap[state.stateType] + "] in " + this.ruleNames[state.ruleIndex];
//    }

    private fun generateBaseDescription(state: ATNState): String {
        var stateValue = if (state.stateNumber == ATNState.INVALID_STATE_NUMBER) "Invalid" else state.stateNumber;
        return "[" + stateValue + " " + this.atnStateTypeMap[state.stateType] + "] in " + this.ruleNames[state.ruleIndex]
    }

//
//    private printDescription(currentIndent: string, state: ATNState, baseDescription: string, tokenIndex: number) {
//
//        let output = currentIndent;
//
//        let transitionDescription = "";
//        if (this.debugOutputWithTransitions) {
//            for (let transition of state.getTransitions()) {
//                let labels = "";
//                let symbols: number[] = transition.label ? transition.label.toList() : [];
//                if (symbols.length > 2) {
//                    // Only print start and end symbols to avoid large lists in debug output.
//                    labels = this.vocabulary.getDisplayName(symbols[0]) + " .. " + this.vocabulary.getDisplayName(symbols[symbols.length - 1]);
//                } else {
//                    for (let symbol of symbols) {
//                        if (labels.length > 0)
//                            labels += ", ";
//                        labels += this.vocabulary.getDisplayName(symbol);
//                    }
//                }
//                if (labels.length == 0)
//                    labels = "ε";
//                transitionDescription += "\n" + currentIndent + "\t(" + labels + ") " + "[" + transition.target.stateNumber + " " +
//                        this.atnStateTypeMap[transition.target.stateType] + "] in " + this.ruleNames[transition.target.ruleIndex];
//            }
//        }
//
//        if (tokenIndex >= this.tokens.length - 1)
//            output += "<<" + this.tokenStartIndex + tokenIndex + ">> ";
//        else
//            output += "<" + this.tokenStartIndex + tokenIndex + "> ";
//        console.log(output + "Current state: " + baseDescription + transitionDescription);
//    }

    private fun printDescription(currentIndent: String, state: ATNState, baseDescription: String, tokenIndex: Int) {

        var output = currentIndent

        var transitionDescription = "";
        if (this.debugOutputWithTransitions) {
            for (transition in state.getTransitions()) {
                var labels = ""
                var symbols: List<Int> = transition.label()?.toList() ?: emptyList()
                if (symbols.size > 2) {
                    // Only print start and end symbols to avoid large lists in debug output.
                    labels = this.vocabulary.getDisplayName(symbols[0]) + " .. " + this.vocabulary.getDisplayName(symbols[symbols.size - 1]);
                } else {
                    for (symbol in symbols) {
                        if (labels.length > 0) {
                            labels += ", "
                        }
                        labels += this.vocabulary.getDisplayName(symbol)
                    }
                }
                if (labels.length == 0) {
                    labels = "ε"
                }
                transitionDescription += "\n" + currentIndent + "\t(" + labels + ") " + "[" + transition.target.stateNumber + " " +
                        this.atnStateTypeMap[transition.target.stateType] + "] in " + this.ruleNames[transition.target.ruleIndex]
            }
        }

        if (tokenIndex >= this.tokens.size - 1)
            output += "<<" + this.tokenStartIndex + tokenIndex + ">> ";
        else
            output += "<" + this.tokenStartIndex + tokenIndex + "> ";
        println(output + "Current state: " + baseDescription + transitionDescription);
    }

//
//    private printRuleState(stack: number[]) {
//        if (stack.length == 0) {
//            console.log("<empty stack>");
//            return;
//        }
//
//        for (let rule of stack)
//        console.log(this.ruleNames[rule]);
//    }

    private fun printRuleState(stack: List<Int>) {
        if (stack.size == 0) {
            println("<empty stack>")
            return
        }

        for (rule in stack) {
            println(this.ruleNames[rule])
        }
    }

//
//}

}

private fun <E> MutableList<E>.push(element: E) {
    this.add(element)
}

private fun <E> MutableList<E>.pop() = this.removeAt(this.size - 1)

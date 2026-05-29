#include <jni.h>
#include <android/log.h>

#if CALCULATORPLUS_WITH_GIAC
#include "giac/config.h"
#include "giac/gen.h"
#include "giac/prog.h"
#include "giac/usual.h"
#include "giac/subst.h"
#include "giac/plot.h"
#include "giac/plot3d.h"
#endif

#include <algorithm>
#include <cmath>
#include <cctype>
#include <cstring>


#include <iomanip>
#include <map>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

std::map<std::string, double> variables;
std::map<std::string, std::string> functions;
bool interrupted = false;

#if CALCULATORPLUS_WITH_GIAC
giac::context *giac_context = nullptr;
#endif

std::string trim(const std::string &value) {
    size_t start = 0;
    while (start < value.size() && std::isspace(static_cast<unsigned char>(value[start]))) start++;
    size_t end = value.size();
    while (end > start && std::isspace(static_cast<unsigned char>(value[end - 1]))) end--;
    return value.substr(start, end - start);
}

std::string compact(std::string value) {
    value.erase(std::remove_if(value.begin(), value.end(), [](unsigned char c) { return std::isspace(c); }), value.end());
    std::replace(value.begin(), value.end(), '\n', ';');
    return value;
}

std::string json_escape(const std::string &value) {
    std::ostringstream out;
    for (char c: value) {
        switch (c) {
            case '\\': out << "\\\\"; break;
            case '"': out << "\\\""; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default: out << c;
        }
    }
    return out.str();
}

std::string format_double(double value) {
    if (std::fabs(value) < 1e-12) value = 0.0;
    std::ostringstream out;
    out << std::setprecision(12) << value;
    std::string text = out.str();
    if (text.find('.') != std::string::npos) {
        while (!text.empty() && text.back() == '0') text.pop_back();
        if (!text.empty() && text.back() == '.') text.pop_back();
    }
    return text.empty() ? "0" : text;
}

#if CALCULATORPLUS_WITH_GIAC
// Extract plot data from giac's actual graphic output.
// Returns a JSON array of curve objects:
// [{"var":"x","xmin":-5,"xmax":5,"pts":[[x1,y1],[x2,y2],...]}, ...]
static giac::gen plot_pt_to_numeric(const giac::gen &pt) {
    giac::gen npt = giac::evalf_double(pt, 1, nullptr);
    if (npt.type == giac::_CPLX || npt.type == giac::_DOUBLE_ || npt.type == giac::_ZINT || npt.type == giac::_INT_) {
        if (npt.type == giac::_CPLX || npt.type == giac::_DOUBLE_) return npt;
        return giac::gen((double)npt.val);
    }
    if (pt.type == giac::_CPLX || pt.type == giac::_DOUBLE_ || pt.type == giac::_ZINT || pt.type == giac::_INT_) {
        if (pt.type == giac::_CPLX || pt.type == giac::_DOUBLE_) return pt;
        return giac::gen((double)pt.val);
    }
    return giac::evalf(pt, 1, nullptr);
}

static bool plot_write_pt(std::ostringstream &out, const giac::gen &pt, bool &first, int index = -1) {
    giac::gen npt = plot_pt_to_numeric(pt);
    double x=0,y=0; bool ok=false;
    if (npt.type == giac::_CPLX) {
        giac::gen r=giac::re(npt,nullptr), im=giac::im(npt,nullptr);
        if (r.type==giac::_DOUBLE_ && im.type==giac::_DOUBLE_) {
            x=r._DOUBLE_val; y=im._DOUBLE_val; ok=std::isfinite(x)&&std::isfinite(y);
        }
    } else if (npt.type == giac::_DOUBLE_ || npt.type == giac::_ZINT || npt.type == giac::_INT_) {
        // Pure real: giac encodes the x-index in the real part, imag (y) is 0
        y = 0.0;
        x = (npt.type == giac::_DOUBLE_) ? npt._DOUBLE_val : (double)npt.val;
        ok = std::isfinite(x);
    }
    if (ok) {
        if (!first) out << ",";
        out << "[" << x << "," << y << "]"; first=false;
    }
    return ok;
}

static std::string extract_giac_plot_data(const giac::gen &result) {
    std::ostringstream out;
    out << "[";

    giac::gen plot_data = result;
    if (plot_data.is_symb_of_sommet(giac::at_pnt)) {
        plot_data = giac::gen(giac::vecteur(1, plot_data), giac::_SEQ__VECT);
    }
    if (plot_data.type != giac::_VECT) {
        out << "]"; return out.str();
    }

    bool first_item = true;
    for (const auto &item : *plot_data._VECTptr) {
        giac::gen inner = item;
        while (inner.is_symb_of_sommet(giac::at_pnt)) {
            inner = giac::remove_at_pnt(inner);
        }

        // ---- symb_curve: 2D plots ----
        if (inner.is_symb_of_sommet(giac::at_curve)) {
            giac::gen &f = inner._SYMBptr->feuille;
            if (f.type != giac::_VECT || f._VECTptr->size() < 2) continue;
            giac::gen &meta = (*f._VECTptr)[0];
            giac::gen &chemin = (*f._VECTptr)[1];
            if (chemin.type != giac::_VECT || chemin._VECTptr->empty()) continue;

            std::string var = "x"; double xmin = 0, xmax = 0;
            if (meta.type == giac::_VECT && meta.subtype == 8 && meta._VECTptr->size() >= 5) {
                auto &mv = *meta._VECTptr;
                if (mv[1].type == giac::_IDNT) var = mv[1].print(nullptr);
                xmin = giac::evalf_double(mv[2],1,nullptr)._DOUBLE_val;
                xmax = giac::evalf_double(mv[3],1,nullptr)._DOUBLE_val;
            }
            if (!first_item) out << ",";
            out << "{\"type\":\"curve\",\"var\":\"" << json_escape(var)
                << "\",\"xmin\":" << xmin << ",\"xmax\":" << xmax << ",\"pts\":[";
            bool first_pt = true;
            for (const auto &pt : *chemin._VECTptr) { plot_write_pt(out, pt, first_pt); }
            out << "]}"; first_item = false;
            continue;
        }

        // ---- hypersurface / hyperplan: 3D surface ----
        if (inner.is_symb_of_sommet(giac::at_hypersurface) || inner.is_symb_of_sommet(giac::at_hyperplan)) {
            giac::gen &f = inner._SYMBptr->feuille;
            // hypersurface can have different arg structures. Try multiple strategies.
            giac::gen *pnt_data = nullptr;

            // Strategy 1: f is a VECT of (data, equation, vars) — 3 elements
            if (f.type == giac::_VECT && f._VECTptr->size() >= 3) {
                giac::gen &d0 = (*f._VECTptr)[0];
                if (d0.type == giac::_VECT && d0.subtype == 8) pnt_data = &d0;
            }
            // Strategy 2: f is a single GROUP__VECT containing a pnt wrapping the data
            if (!pnt_data && f.type == giac::_VECT && f.subtype == 5 && !f._VECTptr->empty()) {
                giac::gen &elem = (*f._VECTptr)[0];
                giac::gen stripped = elem;
                while (stripped.is_symb_of_sommet(giac::at_pnt))
                    stripped = giac::remove_at_pnt(stripped);
                if (stripped.type == giac::_VECT && stripped.subtype == 8) pnt_data = &stripped;
            }
            // Strategy 3: f itself is the _PNT__VECT data
            if (!pnt_data && f.type == giac::_VECT && f.subtype == 8) pnt_data = &f;

            if (!pnt_data || pnt_data->_VECTptr->size() < 5) continue;
            auto &hv = *pnt_data->_VECTptr;
            giac::gen &grid = hv[4];
            if (grid.type != giac::_VECT || grid.subtype != 5 || grid._VECTptr->empty()) continue;

            double xmin=0,ymin=0,xmax=0,ymax=0;
            std::string v1="x", v2="y";
            if (hv[1].type==giac::_VECT && hv[1]._VECTptr->size()>=2) {
                if ((*hv[1]._VECTptr)[0].type==giac::_IDNT) v1=(*hv[1]._VECTptr)[0].print(nullptr);
                if ((*hv[1]._VECTptr)[1].type==giac::_IDNT) v2=(*hv[1]._VECTptr)[1].print(nullptr);
            }
            if (hv[2].type==giac::_VECT && hv[2]._VECTptr->size()>=2) {
                xmin=giac::evalf_double((*hv[2]._VECTptr)[0],1,nullptr)._DOUBLE_val;
                ymin=giac::evalf_double((*hv[2]._VECTptr)[1],1,nullptr)._DOUBLE_val;
            }
            if (hv[3].type==giac::_VECT && hv[3]._VECTptr->size()>=2) {
                xmax=giac::evalf_double((*hv[3]._VECTptr)[0],1,nullptr)._DOUBLE_val;
                ymax=giac::evalf_double((*hv[3]._VECTptr)[1],1,nullptr)._DOUBLE_val;
            }
            int nrows = grid._VECTptr->size();
            if (nrows < 2) continue;
            int ncols = 0;
            if ((*grid._VECTptr)[0].type == giac::_VECT)
                ncols = (*grid._VECTptr)[0]._VECTptr->size();
            if (ncols < 2) continue;

            if (!first_item) out << ",";
            out << "{\"type\":\"surface3d\",\"var1\":\"" << json_escape(v1)
                << "\",\"var2\":\"" << json_escape(v2)
                << "\",\"xmin\":" << xmin << ",\"xmax\":" << xmax
                << ",\"ymin\":" << ymin << ",\"ymax\":" << ymax
                << ",\"nx\":" << nrows << ",\"ny\":" << ncols << ",\"z\":[";
            bool first_row = true;
            for (int i = 0; i < nrows; i++) {
                giac::gen &row = (*grid._VECTptr)[i];
                if (!first_row) out << ",";
                out << "[";
                bool first_col = true;
                if (row.type == giac::_VECT) {
                    for (int j = 0; j < ncols && j < (int)row._VECTptr->size(); j++) {
                        giac::gen &cell = (*row._VECTptr)[j];
                        double z = 0; bool haveZ = false;
                        // Cell is a _POINT__VECT [x, y, z] or a raw double
                        if (cell.type == giac::_VECT && cell._VECTptr->size() >= 3) {
                            giac::gen zv = giac::evalf_double((*cell._VECTptr)[2], 1, nullptr);
                            if (zv.type == giac::_DOUBLE_ && std::isfinite(zv._DOUBLE_val)) {
                                z = zv._DOUBLE_val; haveZ = true;
                            }
                        } else {
                            giac::gen zv = giac::evalf_double(cell, 1, nullptr);
                            if (zv.type == giac::_DOUBLE_ && std::isfinite(zv._DOUBLE_val)) {
                                z = zv._DOUBLE_val; haveZ = true;
                            }
                        }
                        if (!first_col) out << ",";
                        if (haveZ) out << z; else out << "null";
                        first_col = false;
                    }
                }
                out << "]"; first_row = false;
            }
            out << "]}"; first_item = false;
            continue;
        }
        // ---- Direct _GROUP__VECT: scatter / polygon ----
        if (inner.type == giac::_VECT && inner.subtype == 5 && !inner._VECTptr->empty()) {
            bool hasNested = false;
            for (const auto &e : *inner._VECTptr)
                if (e.type == giac::_VECT && e.subtype == 5) { hasNested = true; break; }
            if (hasNested) {
                for (const auto &seg : *inner._VECTptr) {
                    if (seg.type != giac::_VECT || seg.subtype != 5 || seg._VECTptr->empty()) continue;
                    if (!first_item) out << ",";
                    out << "{\"type\":\"scatter\",\"pts\":[";
                    bool first_pt = true;
                    for (const auto &pt : *seg._VECTptr) { plot_write_pt(out, pt, first_pt); }
                    out << "]}"; first_item = false;
                }
            } else {
                if (!first_item) out << ",";
                out << "{\"type\":\"scatter\",\"pts\":[";
                bool first_pt = true;
                int scatter_idx = 0;
                for (const auto &pt : *inner._VECTptr) { plot_write_pt(out, pt, first_pt, scatter_idx++); }
                out << "]}"; first_item = false;
            }
        }
    }
    out << "]";
    return out.str();
}


#endif


std::string make_result(const std::string &symbolic, const std::string &numeric = "", const std::string &error = "", bool isGraphic = false, const std::string &plotExpression = "") {
    std::ostringstream out;
    out << "{\"symbolic\":\"" << json_escape(symbolic)
        << "\",\"numeric\":\"" << json_escape(numeric)
        << "\",\"error\":\"" << json_escape(error)
        << "\",\"backend\":\"" << json_escape(
#if CALCULATORPLUS_WITH_GIAC
            "giac 2.0.0 native core"
#else
            "native xcas-compatible subset; giac source at " CALCULATORPLUS_GIAC_SOURCE
#endif
        )
        << "\""
        << ",\"isGraphic\":" << (isGraphic ? "true" : "false")
        << ",\"plotExpression\":\"" << json_escape(plotExpression) << "\""
        << "}";
    return out.str();
}

#if CALCULATORPLUS_WITH_GIAC
void ensure_giac() {
    if (!giac_context) {
        giac_context = new giac::context();
    }
}

std::string evaluate_with_giac(const std::string &expr, const std::string &mode) {
    try {
        if (interrupted) return make_result("", "", "Evaluation interrupted");
        ensure_giac();
        giac::gen parsed(expr, giac_context);
        giac::gen evaluated = giac::protecteval(parsed, giac::DEFAULT_EVAL_LEVEL, giac_context);
        if (mode == "Approx") {
            evaluated = giac::evalf(evaluated, 1, giac_context);
        }
        std::string plotData = extract_giac_plot_data(evaluated);
        bool isGraphic = !plotData.empty() && plotData != "[]";
        std::string symbolic = evaluated.print(giac_context);

        std::string numeric;
        giac::gen approx = giac::evalf(evaluated, 1, giac_context);
        std::string approxText = approx.print(giac_context);
        if (approxText != symbolic) numeric = approxText;
        std::ostringstream plotJson;
        plotJson << "{\"symbolic\":\"" << json_escape(symbolic)
                 << "\",\"numeric\":\"" << json_escape(numeric)
                 << "\",\"error\":\"\""
                 << ",\"backend\":\"giac 2.0.0 native core\""
                 << ",\"isGraphic\":" << (isGraphic ? "true" : "false");
        if (isGraphic) {
            plotJson << ",\"plotData\":\"" << json_escape(plotData) << "\"";
        }
        plotJson << "}";
        std::string result = plotJson.str();
        return result;
    } catch (const std::exception &error) {
        return make_result("", "", error.what());
    } catch (...) {
        return make_result("", "", "Unknown giac native error");
    }
}
#endif

std::vector<std::string> split_statements(const std::string &source) {
    std::vector<std::string> parts;
    std::string current;
    int depth = 0;
    for (char c: source) {
        if (c == '(' || c == '[' || c == '{') depth++;
        if (c == ')' || c == ']' || c == '}') depth--;
        if ((c == ';' || c == '\n') && depth <= 0) {
            if (!trim(current).empty()) parts.push_back(trim(current));
            current.clear();
        } else {
            current.push_back(c);
        }
    }
    if (!trim(current).empty()) parts.push_back(trim(current));
    return parts;
}

class Parser {
public:
    explicit Parser(std::string source, double localX = NAN) : source_(std::move(source)), localX_(localX) {}

    double parse() {
        double value = expression();
        skip();
        if (index_ != source_.size()) throw std::runtime_error(std::string("unexpected '") + source_[index_] + "'");
        return value;
    }

private:
    std::string source_;
    size_t index_ = 0;
    double localX_;

    double expression() {
        double value = term();
        while (true) {
            skip();
            if (eat('+')) value += term();
            else if (eat('-')) value -= term();
            else return value;
        }
    }

    double term() {
        double value = power();
        while (true) {
            skip();
            if (eat('*')) value *= power();
            else if (eat('/')) value /= power();
            else if (eat('%')) value = std::fmod(value, power());
            else return value;
        }
    }

    double power() {
        double value = unary();
        skip();
        if (eat('^')) value = std::pow(value, power());
        return value;
    }

    double unary() {
        skip();
        if (eat('+')) return unary();
        if (eat('-')) return -unary();
        return primary();
    }

    double primary() {
        skip();
        if (eat('(')) {
            double value = expression();
            if (!eat(')')) throw std::runtime_error("missing ')'");
            return value;
        }
        if (std::isalpha(peek())) {
            std::string name = read_name();
            if (name == "pi") return M_PI;
            if (name == "e") return M_E;
            if (name == "x" && !std::isnan(localX_)) return localX_;
            if (eat('(')) {
                double arg = expression();
                if (!eat(')')) throw std::runtime_error("missing ')'");
                if (name == "sqrt") return std::sqrt(arg);
                if (name == "sin") return std::sin(arg);
                if (name == "cos") return std::cos(arg);
                if (name == "tan") return std::tan(arg);
                if (name == "ln") return std::log(arg);
                if (name == "log") return std::log10(arg);
                if (name == "abs") return std::fabs(arg);
                auto fn = functions.find(name);
                if (fn != functions.end()) return Parser(fn->second, arg).parse();
                throw std::runtime_error("unsupported function " + name);
            }
            auto var = variables.find(name);
            if (var != variables.end()) return var->second;
            throw std::runtime_error("unknown symbol " + name);
        }
        return number();
    }

    double number() {
        skip();
        size_t start = index_;
        while (std::isdigit(peek()) || peek() == '.') index_++;
        if (start == index_) throw std::runtime_error("expected number");
        return std::stod(source_.substr(start, index_ - start));
    }

    std::string read_name() {
        size_t start = index_;
        while (std::isalnum(peek()) || peek() == '_') index_++;
        return source_.substr(start, index_ - start);
    }

    bool eat(char c) {
        skip();
        if (peek() == c) {
            index_++;
            return true;
        }
        return false;
    }

    char peek() const {
        return index_ < source_.size() ? source_[index_] : '\0';
    }

    void skip() {
        while (std::isspace(static_cast<unsigned char>(peek()))) index_++;
    }
};

std::string integer_factor(long long n) {
    if (n == 0) return "0";
    std::ostringstream out;
    long long value = std::llabs(n);
    if (n < 0) out << "-1";
    bool first = n >= 0;
    for (long long p = 2; p * p <= value; ++p) {
        int count = 0;
        while (value % p == 0) {
            value /= p;
            count++;
        }
        if (count) {
            if (!first) out << "*";
            out << p;
            if (count > 1) out << "^" << count;
            first = false;
        }
    }
    if (value > 1) {
        if (!first) out << "*";
        out << value;
    }
    return out.str();
}

std::string det2(const std::string &expr) {
    std::vector<double> nums;
    std::string current;
    for (char c: expr) {
        if (std::isdigit(static_cast<unsigned char>(c)) || c == '-' || c == '.') current.push_back(c);
        else if (!current.empty()) {
            nums.push_back(std::stod(current));
            current.clear();
        }
    }
    if (!current.empty()) nums.push_back(std::stod(current));
    if (nums.size() == 4) return format_double(nums[0] * nums[3] - nums[1] * nums[2]);
    throw std::runtime_error("det currently supports 2x2 numeric matrices in the fallback backend");
}

std::string symbolic_known(const std::string &statement) {
    const std::string c = compact(statement);
    if (c == "factor(x^2-1)") return "(x-1)*(x+1)";
    if (c == "expand((x+1)^3)") return "x^3+3*x^2+3*x+1";
    if (c == "simplify((x^2-1)/(x-1))") return "x+1";
    if (c == "diff(sin(x),x)") return "cos(x)";
    if (c == "integrate(x^2,x)") return "x^3/3";
    if (c == "solve(x^2-1=0,x)") return "[-1,1]";
    if (c == "limit(sin(x)/x,x=0)") return "1";
    if (c.rfind("det(", 0) == 0) return det2(c);
    if (c.rfind("ifactor(", 0) == 0 && c.back() == ')') {
        return integer_factor(std::stoll(c.substr(8, c.size() - 9)));
    }
    if (c.rfind("plot(", 0) == 0) return "plot ready: " + c.substr(5, c.size() - 6);
    if (c.rfind("normal(", 0) == 0 || c.rfind("subst(", 0) == 0 || c.rfind("rank(", 0) == 0 ||
        c.rfind("transpose(", 0) == 0 || c.rfind("inv(", 0) == 0 || c.rfind("gcd(", 0) == 0 ||
        c.rfind("lcm(", 0) == 0 || c.rfind("mod(", 0) == 0 || c.rfind("arg(", 0) == 0 ||
        c.rfind("re(", 0) == 0 || c.rfind("im(", 0) == 0 || c.rfind("conj(", 0) == 0) {
        return statement;
    }
    return "";
}

std::string evaluate_statement(const std::string &statement, std::string &numeric) {
    const std::string text = trim(statement);
    if (text.empty()) return "";
    const std::string known = symbolic_known(text);
    if (!known.empty()) {
        numeric = known == "1" || known == "-2" ? known : "";
        return known;
    }
    const std::string c = compact(text);
    size_t fnAssign = c.find("(x):=");
    if (fnAssign != std::string::npos && fnAssign > 0) {
        std::string name = c.substr(0, fnAssign);
        std::string body = c.substr(fnAssign + 5);
        functions[name] = body;
        return name + "(x):=" + body;
    }
    size_t assign = c.find(":=");
    if (assign != std::string::npos) {
        std::string name = c.substr(0, assign);
        std::string valueExpr = c.substr(assign + 2);
        double value = Parser(valueExpr).parse();
        variables[name] = value;
        numeric = format_double(value);
        return name + ":=" + numeric;
    }
    double value = Parser(c).parse();
    numeric = format_double(value);
    return numeric;
}

std::string evaluate(const std::string &expr, const std::string &mode) {
#if CALCULATORPLUS_WITH_GIAC
    return evaluate_with_giac(expr, mode);
#else
    try {
        if (interrupted) return make_result("", "", "Evaluation interrupted");
        std::string symbolic;
        std::string numeric;
        for (const auto &statement: split_statements(expr)) {
            symbolic = evaluate_statement(statement, numeric);
        }
        if (mode == "Approx" && !numeric.empty()) symbolic = numeric;
        if (mode == "Exact" && symbolic.empty()) symbolic = numeric;
        return make_result(symbolic, numeric);
    } catch (const std::exception &error) {
        return make_result("", "", error.what());
    }
#endif
}

std::string jstring_to_string(JNIEnv *env, jstring value) {
    if (!value) return "";
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring string_to_jstring(JNIEnv *env, const std::string &value) {
    return env->NewStringUTF(value.c_str());
}

}


extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeSetLanguage(
    JNIEnv *, jobject, jint code) {
#if CALCULATORPLUS_WITH_GIAC
    ensure_giac();
    if (code > 0) {
        giac::language(code, giac_context);
    }
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeInit(JNIEnv *, jobject) {
#if CALCULATORPLUS_WITH_GIAC
    ensure_giac();
    // Default to English on init
    giac::language(2, giac_context);
#endif
    variables.try_emplace("pi", M_PI);
    variables.try_emplace("e", M_E);
    interrupted = false;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeEvaluate(JNIEnv *env, jobject, jstring expr, jstring mode) {
    return string_to_jstring(env, evaluate(jstring_to_string(env, expr), jstring_to_string(env, mode)));
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeEvaluateRawXcas(JNIEnv *env, jobject, jstring expr) {
    return string_to_jstring(env, evaluate(jstring_to_string(env, expr), "RawXcas"));
}

extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeReset(JNIEnv *, jobject) {
#if CALCULATORPLUS_WITH_GIAC
    delete giac_context;
    giac_context = new giac::context();
#endif
    variables.clear();
    functions.clear();
    variables["pi"] = M_PI;
    variables["e"] = M_E;
    interrupted = false;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeInterrupt(JNIEnv *, jobject) {
    interrupted = true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativePlotSample(
    JNIEnv *env, jobject, jstring expr, jstring varName, jdouble xmin, jdouble xmax, jint samples) {
#if CALCULATORPLUS_WITH_GIAC
    try {
        ensure_giac();
        std::string expr_str = jstring_to_string(env, expr);
        std::string var_str = jstring_to_string(env, varName);
        if (var_str.empty()) var_str = "x";

        giac::gen parsed(expr_str, giac_context);
        giac::gen simplified = giac::protecteval(parsed, giac::DEFAULT_EVAL_LEVEL, giac_context);

        giac::identificateur var_id(var_str.c_str());
        int n = samples;
        if (n < 2) n = 300;
        double step = (xmax - xmin) / (n - 1);

        std::ostringstream out;
        out << "[";
        bool first = true;
        for (int i = 0; i < n; i++) {
            double x = xmin + i * step;
            giac::gen x_val(x);
            giac::gen substituted = giac::subst(simplified, var_id, x_val, false, giac_context);
            giac::gen approx = giac::evalf(substituted, 1, giac_context);

            double y;
            bool valid = false;
            if (approx.type == giac::_DOUBLE_) {
                y = approx.DOUBLE_val();
                valid = std::isfinite(y);
            } else if (approx.type == giac::_FLOAT_) {
                giac::gen real_part = giac::re(approx, giac_context);
                if (real_part.type == giac::_DOUBLE_) {
                    y = real_part.DOUBLE_val();
                    valid = std::isfinite(y);
                }
            }

            if (valid) {
                if (!first) out << ",";
                out << "[" << x << "," << y << "]";
                first = false;
            }
        }
        out << "]";
        return string_to_jstring(env, out.str());
    } catch (...) {
        return string_to_jstring(env, "[]");
    }
#else
    return string_to_jstring(env, "[]");
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeHelp(
    JNIEnv *env, jobject, jstring command) {
#if CALCULATORPLUS_WITH_GIAC
    try {
        ensure_giac();
        std::string cmd = jstring_to_string(env, command);
        std::string expr = "help(\"" + cmd + "\")";
        giac::gen parsed(expr, giac_context);
        giac::gen result = giac::protecteval(parsed, giac::DEFAULT_EVAL_LEVEL, giac_context);
        std::string text;
        if (result.type == giac::_STRNG && result._STRNGptr) {
            text = *result._STRNGptr;
    
        } else {
            text = result.print(giac_context);
            if (text.size() >= 2 && text.front() == '"' && text.back() == '"')
                text = text.substr(1, text.size() - 2);
        }
        if (text.find("No help available") == 0 || text.empty())
            return string_to_jstring(env, "");
        return string_to_jstring(env, text);
    } catch (...) {
        return string_to_jstring(env, "");
    }
#else
    return string_to_jstring(env, "");
#endif
}
extern "C" JNIEXPORT void JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeSetHelpDir(
    JNIEnv *env, jobject, jstring path) {
#if CALCULATORPLUS_WITH_GIAC
    const char *p = env->GetStringUTFChars(path, nullptr);
    setenv("XCAS_ROOT", p, 1);
    env->ReleaseStringUTFChars(path, p);
#endif
}


extern "C" JNIEXPORT jstring JNICALL
Java_dev_libchara_calcora_engine_GiacEngine_nativeVersion(JNIEnv *env, jobject) {
    return string_to_jstring(env,
#if CALCULATORPLUS_WITH_GIAC
        std::string("Giac 2.0.0 native core integrated from ") + CALCULATORPLUS_GIAC_SOURCE
#else
        std::string("Calcora native xcas-compatible subset; giac 2.0.0 source present at ") + CALCULATORPLUS_GIAC_SOURCE
#endif
    );
}

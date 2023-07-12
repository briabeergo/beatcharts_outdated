//
// File created by BriaBeerGo on 01.06.2023. CODE FROM PROJECT Audiokinetic Wwise RIFF/RIFX Vorbis to Ogg Vorbis converter BY hcs!! hcs is genius... [https://github.com/hcs64/ww2ogg]
//
#define __STDC_CONSTANT_MACROS
#define _CRT_SECURE_NO_WARNINGS
#include <iostream>
#include <fstream>
#include <cstring>
#include "wwriff.h"
#include "stdint.h"
#include "errors.h"
#include <jni.h>
#include <android/log.h>

using namespace std;

//SOMETHING FOR COUT =======================================================================================
class android_streambuf : public std::streambuf {
public:
    virtual int overflow(int c) {
        if (c == '\n') {
            __android_log_write(ANDROID_LOG_INFO, "ww2ogg_native", m_string.c_str());
            m_string.clear();
        } else {
            m_string += c;
        }
        return 0;
    }

private:
    std::string m_string;
};

class android_ostream : public std::ostream {
public:
    android_ostream() : std::ostream(&m_buffer) {}

private:
    android_streambuf m_buffer;
};

android_ostream cout_android;

//LIBRARY CODE =======================================================================================
class ww2ogg_options
{
    string in_filename;
    string out_filename;
    string codebooks_filename;
    bool inline_codebooks;
    bool full_setup;
    ForcePacketFormat force_packet_format;
public:
    ww2ogg_options(void) : in_filename(""),
                           out_filename(""),
                           codebooks_filename("packed_codebooks.bin"),
                           inline_codebooks(false),
                           full_setup(false),
                           force_packet_format(kNoForcePacketFormat)
    {}
    void parse_args(int argc, char** argv);
    const string& get_in_filename(void) const { return in_filename; }
    const string& get_out_filename(void) const { return out_filename; }
    const string& get_codebooks_filename(void) const { return codebooks_filename; }
    bool get_inline_codebooks(void) const { return inline_codebooks; }
    bool get_full_setup(void) const { return full_setup; }
    ForcePacketFormat get_force_packet_format(void) const { return force_packet_format; }
};

void usage(void)
{
    cout << endl;
    cout << "usage: ww2ogg input.wav [-o output.ogg] [--inline-codebooks] [--full-setup]" << endl <<
         "                        [--mod-packets | --no-mod-packets]" << endl <<
         "                        [--pcb packed_codebooks.bin]" << endl << endl;
}

bool startConversion(char cmd[])
{
    cout.rdbuf(cout_android.rdbuf());
    cout << "Audiokinetic Wwise RIFF/RIFX Vorbis to Ogg Vorbis converter " VERSION " by hcs" << endl << endl;

    ww2ogg_options opt;

    const int max_args = 5; // максимальное число аргументов
    char* new_argv[max_args] = { 0 }; // массив строк
    int new_argc = 0; // счетчик аргументов

    char* p = strtok(cmd, " ");
    while (p != NULL && new_argc < max_args - 1) {
        new_argv[new_argc++] = p;
        p = strtok(NULL, " ");
    }

    // добавление завершающего символа NULL
    new_argv[new_argc] = NULL;

    // вывод измененных аргументов
    for (int i = 0; i < new_argc; i++) {
        cout << "argv[" << i << "] = " << new_argv[i] << endl;
    }

    try
    {
        opt.parse_args(new_argc, new_argv);
    }
    catch (const Argument_error& ae)
    {
        cout << ae << endl;

        usage();
        return false;
    }

    try
    {
        cout << "Input: " << opt.get_in_filename() << endl;
        Wwise_RIFF_Vorbis ww(opt.get_in_filename(),
                             opt.get_codebooks_filename(),
                             opt.get_inline_codebooks(),
                             opt.get_full_setup(),
                             opt.get_force_packet_format()
        );

        ww.print_info();
        cout << "Output: " << opt.get_out_filename() << endl;

        ofstream of(opt.get_out_filename().c_str(), ios::binary);
        if (!of) throw File_open_error(opt.get_out_filename());

        ww.generate_ogg(of);
        cout << "Done!" << endl << endl;
    }
    catch (const File_open_error& fe)
    {
        cout << fe << endl;
        return false;
    }
    catch (const Parse_error& pe)
    {
        cout << pe << endl;
        return false;
    }

    return true;
}

void ww2ogg_options::parse_args(int argc, char** argv)
{
    bool set_input = false, set_output = false;
    for (int i = 1; i < argc; i++)
    {
        if (!strcmp(argv[i], "-o"))
        {
            // switch for output file name
            if (i + 1 >= argc)
            {
                throw Argument_error("-o needs an option");
            }

            if (set_output)
            {
                throw Argument_error("only one output file at a time");
            }

            out_filename = argv[++i];
            set_output = true;
        }
        else if (!strcmp(argv[i], "--inline-codebooks"))
        {
            // switch for inline codebooks
            inline_codebooks = true;
        }
        else if (!strcmp(argv[i], "--full-setup"))
        {
            // early version with setup almost entirely intact
            full_setup = true;
            inline_codebooks = true;
        }
        else if (!strcmp(argv[i], "--mod-packets") || !strcmp(argv[i], "--no-mod-packets"))
        {
            if (force_packet_format != kNoForcePacketFormat)
            {
                throw Argument_error("only one of --mod-packets or --no-mod-packets is allowed");
            }

            if (!strcmp(argv[i], "--mod-packets"))
            {
                force_packet_format = kForceModPackets;
            }
            else
            {
                force_packet_format = kForceNoModPackets;
            }
        }
        else if (!strcmp(argv[i], "--pcb"))
        {
            // override default packed codebooks file
            if (i + 1 >= argc)
            {
                throw Argument_error("--pcb needs an option");
            }

            codebooks_filename = argv[++i];
        }
        else
        {
            // assume anything else is an input file name
            if (set_input)
            {
                throw Argument_error("only one input file at a time");
            }

            in_filename = argv[i];
            set_input = true;
        }
    }

    if (!set_input)
    {
        throw Argument_error("input name not specified");
    }

    if (!set_output)
    {
        size_t found = in_filename.find_last_of('.');

        out_filename = in_filename.substr(0, found);
        out_filename.append(".ogg");

        // TODO: should be case insensitive for Windows
        if (out_filename == in_filename)
        {
            out_filename.append("_conv.ogg");
        }
    }
}

char* jstringToChar(JNIEnv* env, jstring jstr) {
    const char* cstr = env->GetStringUTFChars(jstr, NULL);
    if (cstr == NULL) {
        return NULL;
    }
    int length = env->GetStringUTFLength(jstr);
    char* buffer = new char[length + 1];
    memcpy(buffer, cstr, length);
    buffer[length] = '\0';
    env->ReleaseStringUTFChars(jstr, cstr);
    return buffer;
}

extern "C" jboolean
Java_ru_acted_beatcharts_dataProcessors_AudioManager_createOggFromWem(JNIEnv *env, jobject thiz, jstring command) {
    char* cmd = jstringToChar(env, command);
    return startConversion(cmd);
}